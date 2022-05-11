package com.ampnet.payoutservice.service

import com.ampnet.payoutservice.blockchain.BlockchainService
import com.ampnet.payoutservice.blockchain.properties.ChainPropertiesHandler
import com.ampnet.payoutservice.config.ApplicationProperties
import com.ampnet.payoutservice.exception.ErrorCode
import com.ampnet.payoutservice.exception.InvalidRequestException
import com.ampnet.payoutservice.model.params.CreateSnapshotParams
import com.ampnet.payoutservice.model.params.FetchMerkleTreeParams
import com.ampnet.payoutservice.model.result.FullSnapshot
import com.ampnet.payoutservice.model.result.FullSnapshotData
import com.ampnet.payoutservice.model.result.OptionalSnapshotData
import com.ampnet.payoutservice.model.result.PendingSnapshot
import com.ampnet.payoutservice.model.result.Snapshot
import com.ampnet.payoutservice.model.result.SuccessfulSnapshotData
import com.ampnet.payoutservice.repository.MerkleTreeRepository
import com.ampnet.payoutservice.repository.SnapshotRepository
import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.HashFunction
import com.ampnet.payoutservice.util.MerkleTree
import com.ampnet.payoutservice.util.SnapshotFailureCause
import com.ampnet.payoutservice.util.SnapshotStatus
import com.ampnet.payoutservice.util.WalletAddress
import mu.KLogging
import org.springframework.beans.factory.DisposableBean
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.TimeUnit

@Service
class SnapshotQueueServiceImpl(
    private val merkleTreeRepository: MerkleTreeRepository,
    private val snapshotRepository: SnapshotRepository,
    private val ipfsService: IpfsService,
    private val blockchainService: BlockchainService,
    private val applicationProperties: ApplicationProperties,
    scheduledExecutorServiceProvider: ScheduledExecutorServiceProvider
) : SnapshotQueueService, DisposableBean {

    companion object : KLogging() {
        const val QUEUE_NAME = "SnapshotQueue"
    }

    private val executorService = scheduledExecutorServiceProvider.newSingleThreadScheduledExecutor(QUEUE_NAME)

    init {
        executorService.scheduleAtFixedRate(
            { processSnapshots() },
            applicationProperties.createPayoutQueue.initialDelay,
            applicationProperties.createPayoutQueue.polling,
            TimeUnit.MILLISECONDS
        )
    }

    override fun destroy() {
        logger.info { "Shutting down snapshot queue executor service..." }
        executorService.shutdown()
    }

    override fun submitSnapshot(params: CreateSnapshotParams): UUID {
        logger.info { "Snapshot request with params: $params" }
        checkAssetOwnerIfNeeded(params.chainId, params.assetAddress, params.ownerAddress)
        return snapshotRepository.createSnapshot(params)
    }

    override fun getSnapshotById(snapshotId: UUID): FullSnapshot? {
        logger.debug { "Fetching snapshot, snapshotId: $snapshotId" }
        return snapshotRepository.getById(snapshotId)?.toResponse()
    }

    override fun getAllSnapshotsByChainIdOwnerAndStatuses(
        chainId: ChainId?,
        owner: WalletAddress?,
        statuses: Set<SnapshotStatus>
    ): List<FullSnapshot> {
        logger.debug { "Fetching all snapshots for chainId: $chainId, owner: $owner, statuses: $statuses" }

        return snapshotRepository.getAllByChainIdOwnerAndStatuses(chainId, owner, statuses)
            .map { it.toResponse() }
    }

    private fun Snapshot.toResponse(): FullSnapshot =
        FullSnapshot(
            id = id,
            name = name,
            chainId = chainId,
            assetAddress = assetAddress,
            blockNumber = blockNumber,
            ignoredHolderAddresses = ignoredHolderAddresses,
            ownerAddress = ownerAddress,
            snapshotStatus = data.status,
            snapshotFailureCause = data.failureCause,
            data = data.createSnapshotData()
        )

    private fun OptionalSnapshotData.createSnapshotData(): FullSnapshotData? {
        return if (this is SuccessfulSnapshotData) {
            val tree = merkleTreeRepository.getById(merkleTreeRootId)

            tree?.let {
                FullSnapshotData(
                    totalAssetAmount = totalAssetAmount,
                    merkleRootHash = it.root.hash,
                    merkleTreeIpfsHash = merkleTreeIpfsHash,
                    merkleTreeDepth = it.root.depth,
                    hashFn = it.hashFn
                )
            }
        } else null
    }

    @Suppress("TooGenericExceptionCaught")
    private fun processSnapshots() {
        snapshotRepository.getPending()?.let { snapshot ->
            try {
                handlePendingSnapshot(snapshot)
            } catch (ex: Throwable) {
                logger.error { "Failed to handle pending snapshot, snapshotId: ${snapshot.id}: ${ex.message}" }

                val cause = when (ex.cause?.message?.contains("Log response size exceeded")) {
                    true -> SnapshotFailureCause.LOG_RESPONSE_LIMIT
                    else -> SnapshotFailureCause.OTHER
                }

                snapshotRepository.failSnapshot(snapshot.id, cause)
            }
        }
    }

    private fun handlePendingSnapshot(snapshot: PendingSnapshot) {
        val contractDeploymentBlock = blockchainService.findContractDeploymentBlockNumber(
            chainId = snapshot.chainId,
            contractAddress = snapshot.assetAddress
        )
        val balances = blockchainService.fetchErc20AccountBalances(
            chainId = snapshot.chainId,
            erc20ContractAddress = snapshot.assetAddress,
            ignoredErc20Addresses = snapshot.ignoredHolderAddresses,
            startBlock = contractDeploymentBlock,
            endBlock = snapshot.blockNumber
        )
        val totalAssetAmount = Balance(balances.sumOf { it.balance.rawValue })

        logger.info { "Total sum of non-ignored asset balances: $totalAssetAmount" }

        val tree = MerkleTree(balances, HashFunction.KECCAK_256)
        val alreadyInsertedTree = merkleTreeRepository.fetchTree(
            FetchMerkleTreeParams(tree.root.hash, snapshot.chainId, snapshot.assetAddress)
        )

        val rootId = if (alreadyInsertedTree != null) {
            logger.debug { "Merkle tree already exists, returning tree ID" }
            alreadyInsertedTree.treeId
        } else {
            logger.debug { "Storing Merkle tree into the database" }
            merkleTreeRepository.storeTree(tree, snapshot.chainId, snapshot.assetAddress, snapshot.blockNumber)
        }

        val ipfsHash = ipfsService.pinJsonToIpfs(tree)

        snapshotRepository.completeSnapshot(snapshot.id, rootId, ipfsHash, totalAssetAmount)
        logger.info { "Snapshot completed: ${snapshot.id}" }
    }

    private fun checkAssetOwnerIfNeeded(
        chainId: ChainId,
        assetAddress: ContractAddress,
        ownerAddress: WalletAddress
    ) {
        if (applicationProperties.payout.checkAssetOwner) {
            val assetOwner = blockchainService.getAssetOwner(chainId, assetAddress)

            if (assetOwner != ownerAddress) {
                logger.warn { "Requesting user is not asset owner" }
                throw InvalidRequestException(
                    ErrorCode.USER_NOT_ASSET_OWNER,
                    "User with wallet address: $ownerAddress is not the owner of asset contract: $assetAddress"
                )
            }
        }
    }
}
