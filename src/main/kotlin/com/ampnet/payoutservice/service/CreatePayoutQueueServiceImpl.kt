package com.ampnet.payoutservice.service

import com.ampnet.payoutservice.blockchain.BlockchainService
import com.ampnet.payoutservice.blockchain.properties.ChainPropertiesHandler
import com.ampnet.payoutservice.config.ApplicationProperties
import com.ampnet.payoutservice.exception.ErrorCode
import com.ampnet.payoutservice.exception.InvalidRequestException
import com.ampnet.payoutservice.model.params.CreateSnapshotParams
import com.ampnet.payoutservice.model.params.FetchMerkleTreeParams
import com.ampnet.payoutservice.model.result.FullCreatePayoutData
import com.ampnet.payoutservice.model.result.FullCreatePayoutTask
import com.ampnet.payoutservice.model.result.OptionalSnapshotData
import com.ampnet.payoutservice.model.result.PendingSnapshot
import com.ampnet.payoutservice.model.result.Snapshot
import com.ampnet.payoutservice.model.result.SuccessfulSnapshotData
import com.ampnet.payoutservice.repository.MerkleTreeRepository
import com.ampnet.payoutservice.repository.SnapshotRepository
import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.HashFunction
import com.ampnet.payoutservice.util.MerkleTree
import com.ampnet.payoutservice.util.SnapshotStatus
import com.ampnet.payoutservice.util.WalletAddress
import mu.KLogging
import org.springframework.beans.factory.DisposableBean
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.TimeUnit

@Service // TODO update ihn SD-708
class CreatePayoutQueueServiceImpl(
    private val merkleTreeRepository: MerkleTreeRepository,
    private val snapshotRepository: SnapshotRepository,
    private val ipfsService: IpfsService,
    private val blockchainService: BlockchainService,
    private val applicationProperties: ApplicationProperties,
    scheduledExecutorServiceProvider: ScheduledExecutorServiceProvider
) : CreatePayoutQueueService, DisposableBean {

    companion object : KLogging() {
        const val QUEUE_NAME = "CreatePayoutQueue"
    }

    private val executorService = scheduledExecutorServiceProvider.newSingleThreadScheduledExecutor(QUEUE_NAME)
    private val chainHandler = ChainPropertiesHandler(applicationProperties)

    init {
        executorService.scheduleAtFixedRate(
            { processTasks() },
            applicationProperties.createPayoutQueue.initialDelay,
            applicationProperties.createPayoutQueue.polling,
            TimeUnit.MILLISECONDS
        )
    }

    override fun destroy() {
        logger.info { "Shutting down create payout task queue executor service..." }
        executorService.shutdown()
    }

    override fun submitTask(params: CreateSnapshotParams): UUID {
        logger.info { "Payout request with params: $params" }
        checkAssetOwnerIfNeeded(params.chainId, params.assetAddress, params.ownerAddress)
        return snapshotRepository.createSnapshot(params)
    }

    override fun getTaskById(taskId: UUID): FullCreatePayoutTask? {
        logger.debug { "Fetching create payout task, taskId: $taskId" }
        return snapshotRepository.getById(taskId)?.toResponse()
    }

    override fun getAllTasksByIssuerAndOwner(
        chainId: ChainId, // TODO make optional in SD-708
        issuer: ContractAddress?,
        owner: WalletAddress?,
        statuses: Set<SnapshotStatus>
    ): List<FullCreatePayoutTask> {
        logger.debug {
            "Fetching all create payout tasks for chainId: $chainId, issuer: $issuer," +
                " owner: $owner, statuses: $statuses"
        }

        return snapshotRepository.getAllByChainIdOwnerAndStatuses(chainId, owner, statuses)
            .map { it.toResponse() }
    }

    private fun Snapshot.toResponse(): FullCreatePayoutTask =
        FullCreatePayoutTask(
            taskId = id,
            chainId = chainId,
            assetAddress = assetAddress,
            payoutBlockNumber = blockNumber,
            ignoredAssetAddresses = ignoredHolderAddresses,
            requesterAddress = ownerAddress,
            issuerAddress = null, // TODO remove in SD-708
            snapshotStatus = data.status,
            data = data.createPayoutData()
        )

    private fun OptionalSnapshotData.createPayoutData(): FullCreatePayoutData? {
        return if (this is SuccessfulSnapshotData) {
            val tree = merkleTreeRepository.getById(merkleTreeRootId)

            tree?.let {
                FullCreatePayoutData(
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
    private fun processTasks() {
        snapshotRepository.getPending()?.let { task ->
            try {
                handlePendingCreatePayoutTask(task)
            } catch (ex: Throwable) {
                logger.error { "Failed to handle pending create payout task, taskId: ${task.id}: ${ex.message}" }
                snapshotRepository.failSnapshot(task.id)
            }
        }
    }

    private fun handlePendingCreatePayoutTask(task: PendingSnapshot) {
        val balances = blockchainService.fetchErc20AccountBalances(
            chainId = task.chainId,
            erc20ContractAddress = task.assetAddress,
            ignoredErc20Addresses = task.ignoredHolderAddresses,
            startBlock = chainHandler.getChainProperties(task.chainId)?.startBlockNumber?.let { BlockNumber(it) },
            endBlock = task.blockNumber
        )
        val totalAssetAmount = Balance(balances.sumOf { it.balance.rawValue })

        logger.info { "Total sum of non-ignored asset balances: $totalAssetAmount" }

        val tree = MerkleTree(balances, HashFunction.KECCAK_256)
        val alreadyInsertedTree = merkleTreeRepository.fetchTree(
            FetchMerkleTreeParams(tree.root.hash, task.chainId, task.assetAddress)
        )

        val rootId = if (alreadyInsertedTree != null) {
            logger.debug { "Merkle tree already exists, returning tree ID" }
            alreadyInsertedTree.treeId
        } else {
            logger.debug { "Storing Merkle tree into the database" }
            merkleTreeRepository.storeTree(tree, task.chainId, task.assetAddress, task.blockNumber)
        }

        val ipfsHash = ipfsService.pinJsonToIpfs(tree)

        snapshotRepository.completeSnapshot(task.id, rootId, ipfsHash, totalAssetAmount)
        logger.info { "Task completed: ${task.id}" }
    }

    private fun checkAssetOwnerIfNeeded(
        chainId: ChainId,
        assetAddress: ContractAddress,
        requesterAddress: WalletAddress
    ) {
        if (applicationProperties.payout.checkAssetOwner) {
            val assetOwner = blockchainService.getAssetOwner(chainId, assetAddress)

            if (assetOwner != requesterAddress) {
                logger.warn { "Requester is not asset owner" }
                throw InvalidRequestException(
                    ErrorCode.USER_NOT_ASSET_OWNER,
                    "User with wallet address: $requesterAddress is not the owner of asset contract: $assetAddress"
                )
            }
        }
    }
}
