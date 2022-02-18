package service

import com.ampnet.payoutservice.ManualFixedScheduler
import com.ampnet.payoutservice.TestBase
import com.ampnet.payoutservice.blockchain.BlockchainService
import com.ampnet.payoutservice.config.ApplicationProperties
import com.ampnet.payoutservice.controller.response.CreatePayoutData
import com.ampnet.payoutservice.controller.response.CreatePayoutTaskResponse
import com.ampnet.payoutservice.exception.InvalidRequestException
import com.ampnet.payoutservice.model.params.CreatePayoutTaskParams
import com.ampnet.payoutservice.model.params.FetchMerkleTreeParams
import com.ampnet.payoutservice.model.result.CreatePayoutTask
import com.ampnet.payoutservice.model.result.MerkleTreeWithId
import com.ampnet.payoutservice.model.result.OtherTaskData
import com.ampnet.payoutservice.model.result.PendingCreatePayoutTask
import com.ampnet.payoutservice.model.result.SuccessfulTaskData
import com.ampnet.payoutservice.repository.CreatePayoutTaskRepository
import com.ampnet.payoutservice.repository.MerkleTreeRepository
import com.ampnet.payoutservice.service.CreatePayoutQueueServiceImpl
import com.ampnet.payoutservice.service.IpfsService
import com.ampnet.payoutservice.service.ScheduledExecutorServiceProvider
import com.ampnet.payoutservice.util.AccountBalance
import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.HashFunction
import com.ampnet.payoutservice.util.IpfsHash
import com.ampnet.payoutservice.util.MerkleTree
import com.ampnet.payoutservice.util.TaskStatus
import com.ampnet.payoutservice.util.WalletAddress
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.verifyNoMoreInteractions
import java.math.BigInteger
import java.util.UUID
import org.mockito.kotlin.verify as verifyMock

class CreatePayoutQueueServiceTest : TestBase() {

    @Test
    fun mustCorrectlySubmitAndCreatePayoutWhenMerkleTreeDoesNotAlreadyExist() {
        val blockchainService = mock<BlockchainService>()
        val chainId = ChainId(1L)
        val assetAddress = ContractAddress("a")
        val requesterAddress = WalletAddress("1")

        suppose("requesting user is asset owner") {
            given(blockchainService.getAssetOwner(chainId, assetAddress))
                .willReturn(requesterAddress)
        }

        val createPayoutTaskRepository = mock<CreatePayoutTaskRepository>()
        val issuerAddress = ContractAddress("abcd")
        val payoutBlock = BlockNumber(BigInteger.TEN)
        val ignoredAddresses = setOf(WalletAddress("dead"))
        val taskUuid = UUID.randomUUID()
        val params = CreatePayoutTaskParams(
            chainId = chainId,
            assetAddress = assetAddress,
            requesterAddress = requesterAddress,
            issuerAddress = issuerAddress,
            payoutBlock = payoutBlock,
            ignoredAssetAddresses = ignoredAddresses
        )

        suppose("payout task is created in database") {
            given(createPayoutTaskRepository.createPayoutTask(params))
                .willReturn(taskUuid)
        }

        suppose("pending task will be returned") {
            given(createPayoutTaskRepository.getPending())
                .willReturn(
                    PendingCreatePayoutTask(
                        taskId = taskUuid,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredAssetAddresses = ignoredAddresses,
                        requesterAddress = requesterAddress,
                        issuerAddress = issuerAddress
                    )
                )
        }

        val accountBalances = listOf(
            AccountBalance(WalletAddress("2"), Balance(BigInteger.ONE)),
            AccountBalance(WalletAddress("3"), Balance(BigInteger.TWO))
        )
        val totalAssetAmount = BigInteger("3")

        suppose("some asset balances are fetched") {
            given(
                blockchainService.fetchErc20AccountBalances(
                    chainId = chainId,
                    erc20ContractAddress = assetAddress,
                    ignoredErc20Addresses = ignoredAddresses,
                    startBlock = null,
                    endBlock = payoutBlock
                )
            ).willReturn(accountBalances)
        }

        val tree = MerkleTree(accountBalances, HashFunction.KECCAK_256)
        val ipfsService = mock<IpfsService>()
        val ipfsHash = IpfsHash("testIpfsHash")

        suppose("Merkle tree is stored to IPFS") {
            given(ipfsService.pinJsonToIpfs(tree))
                .willReturn(ipfsHash)
        }

        val merkleTreeRepository = mock<MerkleTreeRepository>()

        suppose("Merkle tree does not exist in the database") {
            given(merkleTreeRepository.fetchTree(FetchMerkleTreeParams(tree.root.hash, chainId, assetAddress)))
                .willReturn(null)
        }

        val treeUuid = UUID.randomUUID()

        suppose("Merkle tree is stored in the database and tree ID is returned") {
            given(merkleTreeRepository.storeTree(tree, chainId, assetAddress, payoutBlock))
                .willReturn(treeUuid)
        }

        suppose("Merkle tree can be fetched by ID") {
            given(merkleTreeRepository.getById(treeUuid))
                .willReturn(tree)
        }

        val properties = suppose("asset owner will be checked") {
            ApplicationProperties().apply { payout.checkAssetOwner = true }
        }
        val executorServiceProvider = mock<ScheduledExecutorServiceProvider>()
        val scheduler = ManualFixedScheduler()

        suppose("ManualFixedScheduler will be used") {
            given(executorServiceProvider.newSingleThreadScheduledExecutor(any()))
                .willReturn(scheduler)
        }

        val service = CreatePayoutQueueServiceImpl(
            merkleTreeRepository = merkleTreeRepository,
            createPayoutTaskRepository = createPayoutTaskRepository,
            ipfsService = ipfsService,
            blockchainService = blockchainService,
            applicationProperties = properties,
            scheduledExecutorServiceProvider = executorServiceProvider
        )

        verify("payout task is submitted and correct task ID is returned") {
            val response = service.submitTask(params)

            assertThat(response).withMessage()
                .isEqualTo(taskUuid)
        }

        suppose("task is processed") {
            scheduler.execute()
        }

        suppose("successful task is returned from database") {
            given(createPayoutTaskRepository.getById(taskUuid))
                .willReturn(
                    CreatePayoutTask(
                        taskId = taskUuid,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredAssetAddresses = ignoredAddresses,
                        requesterAddress = requesterAddress,
                        issuerAddress = issuerAddress,
                        data = SuccessfulTaskData(
                            merkleTreeRootId = treeUuid,
                            merkleTreeIpfsHash = ipfsHash,
                            totalAssetAmount = totalAssetAmount
                        )
                    )
                )
        }

        verify("task is successfully executed") {
            val response = service.getTaskById(taskUuid)

            assertThat(response).withMessage()
                .isEqualTo(
                    CreatePayoutTaskResponse(
                        taskId = taskUuid,
                        chainId = chainId.value,
                        assetAddress = assetAddress.rawValue,
                        payoutBlockNumber = payoutBlock.value,
                        ignoredAssetAddresses = ignoredAddresses.mapTo(HashSet()) { it.rawValue },
                        requesterAddress = requesterAddress.rawValue,
                        issuerAddress = issuerAddress.rawValue,
                        taskStatus = TaskStatus.SUCCESS,
                        data = CreatePayoutData(
                            totalAssetAmount = totalAssetAmount,
                            merkleRootHash = tree.root.hash.value,
                            merkleTreeIpfsHash = ipfsHash.value,
                            merkleTreeDepth = tree.root.depth,
                            hashFn = tree.hashFn
                        )
                    )
                )
        }

        verify("correct service and repository calls are made") {
            // submitTask()
            verifyMock(blockchainService)
                .getAssetOwner(chainId, assetAddress)
            verifyMock(createPayoutTaskRepository)
                .createPayoutTask(params)

            // processTasks()
            verifyMock(createPayoutTaskRepository)
                .getPending()

            // handlePendingCreatePayoutTask()
            verifyMock(blockchainService)
                .fetchErc20AccountBalances(
                    chainId = chainId,
                    erc20ContractAddress = assetAddress,
                    ignoredErc20Addresses = ignoredAddresses,
                    startBlock = null,
                    endBlock = payoutBlock
                )
            verifyNoMoreInteractions(blockchainService)

            verifyMock(merkleTreeRepository)
                .fetchTree(FetchMerkleTreeParams(tree.root.hash, chainId, assetAddress))
            verifyMock(merkleTreeRepository)
                .storeTree(tree, chainId, assetAddress, payoutBlock)

            verifyMock(ipfsService).pinJsonToIpfs(tree)
            verifyNoMoreInteractions(ipfsService)

            verifyMock(createPayoutTaskRepository)
                .completeTask(taskUuid, treeUuid, ipfsHash, totalAssetAmount)

            // getTaskById()
            verifyMock(createPayoutTaskRepository)
                .getById(taskUuid)
            verifyNoMoreInteractions(createPayoutTaskRepository)

            verifyMock(merkleTreeRepository)
                .getById(treeUuid)
            verifyNoMoreInteractions(merkleTreeRepository)
        }
    }

    @Test
    fun mustFailTaskWhenExceptionIsThrownDuringProcessing() {
        val blockchainService = mock<BlockchainService>()
        val chainId = ChainId(1L)
        val assetAddress = ContractAddress("a")
        val requesterAddress = WalletAddress("1")

        suppose("requesting user is asset owner") {
            given(blockchainService.getAssetOwner(chainId, assetAddress))
                .willReturn(requesterAddress)
        }

        val createPayoutTaskRepository = mock<CreatePayoutTaskRepository>()
        val issuerAddress = ContractAddress("abcd")
        val payoutBlock = BlockNumber(BigInteger.TEN)
        val ignoredAddresses = setOf(WalletAddress("dead"))
        val taskUuid = UUID.randomUUID()
        val params = CreatePayoutTaskParams(
            chainId = chainId,
            assetAddress = assetAddress,
            requesterAddress = requesterAddress,
            issuerAddress = issuerAddress,
            payoutBlock = payoutBlock,
            ignoredAssetAddresses = ignoredAddresses
        )

        suppose("payout task is created in database") {
            given(createPayoutTaskRepository.createPayoutTask(params))
                .willReturn(taskUuid)
        }

        suppose("pending task will be returned") {
            given(createPayoutTaskRepository.getPending())
                .willReturn(
                    PendingCreatePayoutTask(
                        taskId = taskUuid,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredAssetAddresses = ignoredAddresses,
                        requesterAddress = requesterAddress,
                        issuerAddress = issuerAddress
                    )
                )
        }

        suppose("fetching asset balances throws exception") {
            given(
                blockchainService.fetchErc20AccountBalances(
                    chainId = chainId,
                    erc20ContractAddress = assetAddress,
                    ignoredErc20Addresses = ignoredAddresses,
                    startBlock = null,
                    endBlock = payoutBlock
                )
            ).willThrow(RuntimeException())
        }


        val properties = suppose("asset owner will be checked") {
            ApplicationProperties().apply { payout.checkAssetOwner = true }
        }
        val executorServiceProvider = mock<ScheduledExecutorServiceProvider>()
        val scheduler = ManualFixedScheduler()

        suppose("ManualFixedScheduler will be used") {
            given(executorServiceProvider.newSingleThreadScheduledExecutor(any()))
                .willReturn(scheduler)
        }

        val merkleTreeRepository = mock<MerkleTreeRepository>()
        val ipfsService = mock<IpfsService>()

        val service = CreatePayoutQueueServiceImpl(
            merkleTreeRepository = merkleTreeRepository,
            createPayoutTaskRepository = createPayoutTaskRepository,
            ipfsService = ipfsService,
            blockchainService = blockchainService,
            applicationProperties = properties,
            scheduledExecutorServiceProvider = executorServiceProvider
        )

        verify("payout task is submitted and correct task ID is returned") {
            val response = service.submitTask(params)

            assertThat(response).withMessage()
                .isEqualTo(taskUuid)
        }

        suppose("task is processed") {
            scheduler.execute()
        }

        suppose("failed task is returned from database") {
            given(createPayoutTaskRepository.getById(taskUuid))
                .willReturn(
                    CreatePayoutTask(
                        taskId = taskUuid,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredAssetAddresses = ignoredAddresses,
                        requesterAddress = requesterAddress,
                        issuerAddress = issuerAddress,
                        data = OtherTaskData(TaskStatus.FAILED)
                    )
                )
        }

        verify("task execution failed") {
            val response = service.getTaskById(taskUuid)

            assertThat(response).withMessage()
                .isEqualTo(
                    CreatePayoutTaskResponse(
                        taskId = taskUuid,
                        chainId = chainId.value,
                        assetAddress = assetAddress.rawValue,
                        payoutBlockNumber = payoutBlock.value,
                        ignoredAssetAddresses = ignoredAddresses.mapTo(HashSet()) { it.rawValue },
                        requesterAddress = requesterAddress.rawValue,
                        issuerAddress = issuerAddress.rawValue,
                        taskStatus = TaskStatus.FAILED,
                        data = null
                    )
                )
        }

        verify("correct service and repository calls are made") {
            // submitTask()
            verifyMock(blockchainService)
                .getAssetOwner(chainId, assetAddress)
            verifyMock(createPayoutTaskRepository)
                .createPayoutTask(params)

            // processTasks()
            verifyMock(createPayoutTaskRepository)
                .getPending()

            // handlePendingCreatePayoutTask()
            verifyMock(blockchainService)
                .fetchErc20AccountBalances(
                    chainId = chainId,
                    erc20ContractAddress = assetAddress,
                    ignoredErc20Addresses = ignoredAddresses,
                    startBlock = null,
                    endBlock = payoutBlock
                )
            verifyNoMoreInteractions(blockchainService)

            verifyMock(createPayoutTaskRepository)
                .failTask(taskUuid)

            // getTaskById()
            verifyMock(createPayoutTaskRepository)
                .getById(taskUuid)
            verifyNoMoreInteractions(createPayoutTaskRepository)

            verifyNoInteractions(merkleTreeRepository)
            verifyNoInteractions(ipfsService)
        }
    }

    @Test
    fun mustCorrectlySubmitAndCreatePayoutWhenMerkleTreeAlreadyExist() {
        val blockchainService = mock<BlockchainService>()
        val chainId = ChainId(1L)
        val assetAddress = ContractAddress("a")
        val requesterAddress = WalletAddress("1")

        suppose("requesting user is asset owner") {
            given(blockchainService.getAssetOwner(chainId, assetAddress))
                .willReturn(requesterAddress)
        }

        val createPayoutTaskRepository = mock<CreatePayoutTaskRepository>()
        val issuerAddress = ContractAddress("abcd")
        val payoutBlock = BlockNumber(BigInteger.TEN)
        val ignoredAddresses = setOf(WalletAddress("dead"))
        val taskUuid = UUID.randomUUID()
        val params = CreatePayoutTaskParams(
            chainId = chainId,
            assetAddress = assetAddress,
            requesterAddress = requesterAddress,
            issuerAddress = issuerAddress,
            payoutBlock = payoutBlock,
            ignoredAssetAddresses = ignoredAddresses
        )

        suppose("payout task is created in database") {
            given(createPayoutTaskRepository.createPayoutTask(params))
                .willReturn(taskUuid)
        }

        suppose("pending task will be returned") {
            given(createPayoutTaskRepository.getPending())
                .willReturn(
                    PendingCreatePayoutTask(
                        taskId = taskUuid,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredAssetAddresses = ignoredAddresses,
                        requesterAddress = requesterAddress,
                        issuerAddress = issuerAddress
                    )
                )
        }

        val accountBalances = listOf(
            AccountBalance(WalletAddress("2"), Balance(BigInteger.ONE)),
            AccountBalance(WalletAddress("3"), Balance(BigInteger.TWO))
        )
        val totalAssetAmount = BigInteger("3")

        suppose("some asset balances are fetched") {
            given(
                blockchainService.fetchErc20AccountBalances(
                    chainId = chainId,
                    erc20ContractAddress = assetAddress,
                    ignoredErc20Addresses = ignoredAddresses,
                    startBlock = null,
                    endBlock = payoutBlock
                )
            ).willReturn(accountBalances)
        }

        val tree = MerkleTree(accountBalances, HashFunction.KECCAK_256)
        val ipfsService = mock<IpfsService>()
        val ipfsHash = IpfsHash("testIpfsHash")

        suppose("Merkle tree is stored to IPFS") {
            given(ipfsService.pinJsonToIpfs(tree))
                .willReturn(ipfsHash)
        }

        val merkleTreeRepository = mock<MerkleTreeRepository>()
        val treeUuid = UUID.randomUUID()

        suppose("Merkle tree exists in the database") {
            given(merkleTreeRepository.fetchTree(FetchMerkleTreeParams(tree.root.hash, chainId, assetAddress)))
                .willReturn(MerkleTreeWithId(treeUuid, tree))
        }

        suppose("Merkle tree is stored in the database and tree ID is returned") {
            given(merkleTreeRepository.storeTree(tree, chainId, assetAddress, payoutBlock))
                .willReturn(treeUuid)
        }

        suppose("Merkle tree can be fetched by ID") {
            given(merkleTreeRepository.getById(treeUuid))
                .willReturn(tree)
        }

        val properties = suppose("asset owner will be checked") {
            ApplicationProperties().apply { payout.checkAssetOwner = true }
        }
        val executorServiceProvider = mock<ScheduledExecutorServiceProvider>()
        val scheduler = ManualFixedScheduler()

        suppose("ManualFixedScheduler will be used") {
            given(executorServiceProvider.newSingleThreadScheduledExecutor(any()))
                .willReturn(scheduler)
        }

        val service = CreatePayoutQueueServiceImpl(
            merkleTreeRepository = merkleTreeRepository,
            createPayoutTaskRepository = createPayoutTaskRepository,
            ipfsService = ipfsService,
            blockchainService = blockchainService,
            applicationProperties = properties,
            scheduledExecutorServiceProvider = executorServiceProvider
        )

        verify("payout task is submitted and correct task ID is returned") {
            val response = service.submitTask(params)

            assertThat(response).withMessage()
                .isEqualTo(taskUuid)
        }

        suppose("task is processed") {
            scheduler.execute()
        }

        suppose("successful task is returned from database") {
            given(createPayoutTaskRepository.getById(taskUuid))
                .willReturn(
                    CreatePayoutTask(
                        taskId = taskUuid,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredAssetAddresses = ignoredAddresses,
                        requesterAddress = requesterAddress,
                        issuerAddress = issuerAddress,
                        data = SuccessfulTaskData(
                            merkleTreeRootId = treeUuid,
                            merkleTreeIpfsHash = ipfsHash,
                            totalAssetAmount = totalAssetAmount
                        )
                    )
                )
        }

        verify("task is successfully executed") {
            val response = service.getTaskById(taskUuid)

            assertThat(response).withMessage()
                .isEqualTo(
                    CreatePayoutTaskResponse(
                        taskId = taskUuid,
                        chainId = chainId.value,
                        assetAddress = assetAddress.rawValue,
                        payoutBlockNumber = payoutBlock.value,
                        ignoredAssetAddresses = ignoredAddresses.mapTo(HashSet()) { it.rawValue },
                        requesterAddress = requesterAddress.rawValue,
                        issuerAddress = issuerAddress.rawValue,
                        taskStatus = TaskStatus.SUCCESS,
                        data = CreatePayoutData(
                            totalAssetAmount = totalAssetAmount,
                            merkleRootHash = tree.root.hash.value,
                            merkleTreeIpfsHash = ipfsHash.value,
                            merkleTreeDepth = tree.root.depth,
                            hashFn = tree.hashFn
                        )
                    )
                )
        }

        verify("correct service and repository calls are made") {
            // submitTask()
            verifyMock(blockchainService)
                .getAssetOwner(chainId, assetAddress)
            verifyMock(createPayoutTaskRepository)
                .createPayoutTask(params)

            // processTasks()
            verifyMock(createPayoutTaskRepository)
                .getPending()

            // handlePendingCreatePayoutTask()
            verifyMock(blockchainService)
                .fetchErc20AccountBalances(
                    chainId = chainId,
                    erc20ContractAddress = assetAddress,
                    ignoredErc20Addresses = ignoredAddresses,
                    startBlock = null,
                    endBlock = payoutBlock
                )
            verifyNoMoreInteractions(blockchainService)

            verifyMock(merkleTreeRepository)
                .fetchTree(FetchMerkleTreeParams(tree.root.hash, chainId, assetAddress))

            verifyMock(ipfsService).pinJsonToIpfs(tree)
            verifyNoMoreInteractions(ipfsService)

            verifyMock(createPayoutTaskRepository)
                .completeTask(taskUuid, treeUuid, ipfsHash, totalAssetAmount)

            // getTaskById()
            verifyMock(createPayoutTaskRepository)
                .getById(taskUuid)
            verifyNoMoreInteractions(createPayoutTaskRepository)

            verifyMock(merkleTreeRepository)
                .getById(treeUuid)
            verifyNoMoreInteractions(merkleTreeRepository)
        }
    }

    @Test
    fun mustThrowExceptionWhenRequestingUserIsNotAssetOwner() {
        val blockchainService = mock<BlockchainService>()
        val chainId = ChainId(1L)
        val assetAddress = ContractAddress("a")
        val requesterAddress = WalletAddress("1")

        suppose("requesting user is not asset owner") {
            given(blockchainService.getAssetOwner(chainId, assetAddress))
                .willReturn(WalletAddress("2"))
        }

        val properties = suppose("asset owner will be checked") {
            ApplicationProperties().apply { payout.checkAssetOwner = true }
        }

        val createPayoutTaskRepository = mock<CreatePayoutTaskRepository>()
        val issuerAddress = ContractAddress("abcd")
        val payoutBlock = BlockNumber(BigInteger.TEN)
        val ignoredAddresses = setOf(WalletAddress("dead"))
        val params = CreatePayoutTaskParams(
            chainId = chainId,
            assetAddress = assetAddress,
            requesterAddress = requesterAddress,
            issuerAddress = issuerAddress,
            payoutBlock = payoutBlock,
            ignoredAssetAddresses = ignoredAddresses
        )
        val executorServiceProvider = mock<ScheduledExecutorServiceProvider>()
        val scheduler = ManualFixedScheduler()

        suppose("ManualFixedScheduler will be used") {
            given(executorServiceProvider.newSingleThreadScheduledExecutor(any()))
                .willReturn(scheduler)
        }

        val merkleTreeRepository = mock<MerkleTreeRepository>()
        val ipfsService = mock<IpfsService>()

        val service = CreatePayoutQueueServiceImpl(
            merkleTreeRepository = merkleTreeRepository,
            createPayoutTaskRepository = createPayoutTaskRepository,
            ipfsService = ipfsService,
            blockchainService = blockchainService,
            applicationProperties = properties,
            scheduledExecutorServiceProvider = executorServiceProvider
        )

        verify("InvalidRequestException exception is thrown") {
            assertThrows<InvalidRequestException>(message) {
                service.submitTask(params)
            }
        }

        verify("correct service and repository calls are made") {
            // submitTask()
            verifyMock(blockchainService)
                .getAssetOwner(chainId, assetAddress)

            verifyNoMoreInteractions(blockchainService)
            verifyNoInteractions(merkleTreeRepository)
            verifyNoInteractions(createPayoutTaskRepository)
            verifyNoInteractions(ipfsService)
        }
    }

    @Test
    fun mustCorrectlySubmitCreatePayoutForNonOwnerWhenAssetOwnerIsNotChecked() {
        val chainId = ChainId(1L)
        val assetAddress = ContractAddress("a")
        val requesterAddress = WalletAddress("1")
        val issuerAddress = ContractAddress("abcd")
        val payoutBlock = BlockNumber(BigInteger.TEN)
        val ignoredAddresses = setOf(WalletAddress("dead"))
        val taskUuid = UUID.randomUUID()
        val params = CreatePayoutTaskParams(
            chainId = chainId,
            assetAddress = assetAddress,
            requesterAddress = requesterAddress,
            issuerAddress = issuerAddress,
            payoutBlock = payoutBlock,
            ignoredAssetAddresses = ignoredAddresses
        )

        val createPayoutTaskRepository = mock<CreatePayoutTaskRepository>()

        suppose("payout task is created in database") {
            given(createPayoutTaskRepository.createPayoutTask(params))
                .willReturn(taskUuid)
        }

        suppose("pending task will be returned") {
            given(createPayoutTaskRepository.getPending())
                .willReturn(
                    PendingCreatePayoutTask(
                        taskId = taskUuid,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredAssetAddresses = ignoredAddresses,
                        requesterAddress = requesterAddress,
                        issuerAddress = issuerAddress
                    )
                )
        }

        val accountBalances = listOf(
            AccountBalance(WalletAddress("2"), Balance(BigInteger.ONE)),
            AccountBalance(WalletAddress("3"), Balance(BigInteger.TWO))
        )
        val totalAssetAmount = BigInteger("3")

        val blockchainService = mock<BlockchainService>()

        suppose("some asset balances are fetched") {
            given(
                blockchainService.fetchErc20AccountBalances(
                    chainId = chainId,
                    erc20ContractAddress = assetAddress,
                    ignoredErc20Addresses = ignoredAddresses,
                    startBlock = null,
                    endBlock = payoutBlock
                )
            ).willReturn(accountBalances)
        }

        val tree = MerkleTree(accountBalances, HashFunction.KECCAK_256)
        val ipfsService = mock<IpfsService>()
        val ipfsHash = IpfsHash("testIpfsHash")

        suppose("Merkle tree is stored to IPFS") {
            given(ipfsService.pinJsonToIpfs(tree))
                .willReturn(ipfsHash)
        }

        val merkleTreeRepository = mock<MerkleTreeRepository>()

        suppose("Merkle tree does not exist in the database") {
            given(merkleTreeRepository.fetchTree(FetchMerkleTreeParams(tree.root.hash, chainId, assetAddress)))
                .willReturn(null)
        }

        val treeUuid = UUID.randomUUID()

        suppose("Merkle tree is stored in the database and tree ID is returned") {
            given(merkleTreeRepository.storeTree(tree, chainId, assetAddress, payoutBlock))
                .willReturn(treeUuid)
        }

        suppose("Merkle tree can be fetched by ID") {
            given(merkleTreeRepository.getById(treeUuid))
                .willReturn(tree)
        }

        val properties = suppose("asset owner will not be checked") {
            ApplicationProperties().apply { payout.checkAssetOwner = false }
        }
        val executorServiceProvider = mock<ScheduledExecutorServiceProvider>()
        val scheduler = ManualFixedScheduler()

        suppose("ManualFixedScheduler will be used") {
            given(executorServiceProvider.newSingleThreadScheduledExecutor(any()))
                .willReturn(scheduler)
        }

        val service = CreatePayoutQueueServiceImpl(
            merkleTreeRepository = merkleTreeRepository,
            createPayoutTaskRepository = createPayoutTaskRepository,
            ipfsService = ipfsService,
            blockchainService = blockchainService,
            applicationProperties = properties,
            scheduledExecutorServiceProvider = executorServiceProvider
        )

        verify("payout task is submitted and correct task ID is returned") {
            val response = service.submitTask(params)

            assertThat(response).withMessage()
                .isEqualTo(taskUuid)
        }

        suppose("task is processed") {
            scheduler.execute()
        }

        suppose("successful task is returned from database") {
            given(createPayoutTaskRepository.getById(taskUuid))
                .willReturn(
                    CreatePayoutTask(
                        taskId = taskUuid,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredAssetAddresses = ignoredAddresses,
                        requesterAddress = requesterAddress,
                        issuerAddress = issuerAddress,
                        data = SuccessfulTaskData(
                            merkleTreeRootId = treeUuid,
                            merkleTreeIpfsHash = ipfsHash,
                            totalAssetAmount = totalAssetAmount
                        )
                    )
                )
        }

        verify("task is successfully executed") {
            val response = service.getTaskById(taskUuid)

            assertThat(response).withMessage()
                .isEqualTo(
                    CreatePayoutTaskResponse(
                        taskId = taskUuid,
                        chainId = chainId.value,
                        assetAddress = assetAddress.rawValue,
                        payoutBlockNumber = payoutBlock.value,
                        ignoredAssetAddresses = ignoredAddresses.mapTo(HashSet()) { it.rawValue },
                        requesterAddress = requesterAddress.rawValue,
                        issuerAddress = issuerAddress.rawValue,
                        taskStatus = TaskStatus.SUCCESS,
                        data = CreatePayoutData(
                            totalAssetAmount = totalAssetAmount,
                            merkleRootHash = tree.root.hash.value,
                            merkleTreeIpfsHash = ipfsHash.value,
                            merkleTreeDepth = tree.root.depth,
                            hashFn = tree.hashFn
                        )
                    )
                )
        }

        verify("correct service and repository calls are made") {
            // submitTask()
            verifyMock(createPayoutTaskRepository)
                .createPayoutTask(params)

            // processTasks()
            verifyMock(createPayoutTaskRepository)
                .getPending()

            // handlePendingCreatePayoutTask()
            verifyMock(blockchainService)
                .fetchErc20AccountBalances(
                    chainId = chainId,
                    erc20ContractAddress = assetAddress,
                    ignoredErc20Addresses = ignoredAddresses,
                    startBlock = null,
                    endBlock = payoutBlock
                )
            verifyNoMoreInteractions(blockchainService)

            verifyMock(merkleTreeRepository)
                .fetchTree(FetchMerkleTreeParams(tree.root.hash, chainId, assetAddress))
            verifyMock(merkleTreeRepository)
                .storeTree(tree, chainId, assetAddress, payoutBlock)

            verifyMock(ipfsService).pinJsonToIpfs(tree)
            verifyNoMoreInteractions(ipfsService)

            verifyMock(createPayoutTaskRepository)
                .completeTask(taskUuid, treeUuid, ipfsHash, totalAssetAmount)

            // getTaskById()
            verifyMock(createPayoutTaskRepository)
                .getById(taskUuid)
            verifyNoMoreInteractions(createPayoutTaskRepository)

            verifyMock(merkleTreeRepository)
                .getById(treeUuid)
            verifyNoMoreInteractions(merkleTreeRepository)
        }
    }
}
