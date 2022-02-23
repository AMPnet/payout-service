package com.ampnet.payoutservice.service

import com.ampnet.payoutservice.blockchain.BlockchainService
import com.ampnet.payoutservice.blockchain.properties.ChainPropertiesHandler
import com.ampnet.payoutservice.config.ApplicationProperties
import com.ampnet.payoutservice.controller.response.CreatePayoutData
import com.ampnet.payoutservice.controller.response.CreatePayoutTaskResponse
import com.ampnet.payoutservice.exception.ErrorCode
import com.ampnet.payoutservice.exception.InvalidRequestException
import com.ampnet.payoutservice.model.params.CreatePayoutTaskParams
import com.ampnet.payoutservice.model.params.FetchMerkleTreeParams
import com.ampnet.payoutservice.model.result.CreatePayoutTask
import com.ampnet.payoutservice.model.result.OptionalCreatePayoutTaskData
import com.ampnet.payoutservice.model.result.PendingCreatePayoutTask
import com.ampnet.payoutservice.model.result.SuccessfulTaskData
import com.ampnet.payoutservice.repository.CreatePayoutTaskRepository
import com.ampnet.payoutservice.repository.MerkleTreeRepository
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.HashFunction
import com.ampnet.payoutservice.util.MerkleTree
import com.ampnet.payoutservice.util.WalletAddress
import mu.KLogging
import org.springframework.beans.factory.DisposableBean
import org.springframework.stereotype.Service
import java.util.UUID
import java.util.concurrent.TimeUnit

@Service
class CreatePayoutQueueServiceImpl(
    private val merkleTreeRepository: MerkleTreeRepository,
    private val createPayoutTaskRepository: CreatePayoutTaskRepository,
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

    override fun submitTask(params: CreatePayoutTaskParams): UUID {
        logger.info { "Payout request with params: $params" }
        checkAssetOwnerIfNeeded(params.chainId, params.assetAddress, params.requesterAddress)
        return createPayoutTaskRepository.createPayoutTask(params)
    }

    override fun getTaskById(taskId: UUID): CreatePayoutTaskResponse? {
        logger.debug { "Fetching create payout task, taskId: $taskId" }
        return createPayoutTaskRepository.getById(taskId)?.toResponse()
    }

    override fun getAllTasksByIssuerAndOwner(
        issuer: ContractAddress?,
        owner: WalletAddress?
    ): List<CreatePayoutTaskResponse> {
        logger.debug { "Fetching all create payout tasks for issuer: $issuer, owner: $owner" }
        return createPayoutTaskRepository.getAllByIssuerAndOwner(issuer, owner)
            .map { it.toResponse() }
    }

    private fun CreatePayoutTask.toResponse(): CreatePayoutTaskResponse =
        CreatePayoutTaskResponse(
            taskId = taskId,
            chainId = chainId.value,
            assetAddress = assetAddress.rawValue,
            payoutBlockNumber = blockNumber.value,
            ignoredAssetAddresses = ignoredAssetAddresses.mapTo(HashSet()) { it.rawValue },
            requesterAddress = requesterAddress.rawValue,
            issuerAddress = issuerAddress?.rawValue,
            taskStatus = data.status,
            data = data.createPayoutData()
        )

    private fun OptionalCreatePayoutTaskData.createPayoutData(): CreatePayoutData? {
        return if (this is SuccessfulTaskData) {
            val tree = merkleTreeRepository.getById(merkleTreeRootId)

            tree?.let {
                CreatePayoutData(
                    totalAssetAmount = totalAssetAmount,
                    merkleRootHash = it.root.hash.value,
                    merkleTreeIpfsHash = merkleTreeIpfsHash.value,
                    merkleTreeDepth = it.root.depth,
                    hashFn = it.hashFn
                )
            }
        } else null
    }

    @Suppress("TooGenericExceptionCaught")
    private fun processTasks() {
        createPayoutTaskRepository.getPending()?.let { task ->
            try {
                handlePendingCreatePayoutTask(task)
            } catch (ex: Throwable) {
                logger.error { "Failed to handle pending create payout task, taskId: ${task.taskId}: ${ex.message}" }
                createPayoutTaskRepository.failTask(task.taskId)
            }
        }
    }

    private fun handlePendingCreatePayoutTask(task: PendingCreatePayoutTask) {
        val balances = blockchainService.fetchErc20AccountBalances(
            chainId = task.chainId,
            erc20ContractAddress = task.assetAddress,
            ignoredErc20Addresses = task.ignoredAssetAddresses,
            startBlock = chainHandler.getChainProperties(task.chainId)?.startBlockNumber?.let { BlockNumber(it) },
            endBlock = task.blockNumber
        )
        val totalAssetAmount = balances.sumOf { it.balance.rawValue }

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

        createPayoutTaskRepository.completeTask(task.taskId, rootId, ipfsHash, totalAssetAmount)
        logger.info { "Task completed: ${task.taskId}" }
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
