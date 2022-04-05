package service

import com.ampnet.payoutservice.ManualFixedScheduler
import com.ampnet.payoutservice.TestBase
import com.ampnet.payoutservice.blockchain.BlockchainService
import com.ampnet.payoutservice.config.ApplicationProperties
import com.ampnet.payoutservice.exception.InvalidRequestException
import com.ampnet.payoutservice.model.params.CreateSnapshotParams
import com.ampnet.payoutservice.model.params.FetchMerkleTreeParams
import com.ampnet.payoutservice.model.result.FullCreatePayoutData
import com.ampnet.payoutservice.model.result.FullCreatePayoutTask
import com.ampnet.payoutservice.model.result.MerkleTreeWithId
import com.ampnet.payoutservice.model.result.OtherSnapshotData
import com.ampnet.payoutservice.model.result.PendingSnapshot
import com.ampnet.payoutservice.model.result.Snapshot
import com.ampnet.payoutservice.model.result.SuccessfulSnapshotData
import com.ampnet.payoutservice.repository.MerkleTreeRepository
import com.ampnet.payoutservice.repository.SnapshotRepository
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
import com.ampnet.payoutservice.util.SnapshotStatus
import com.ampnet.payoutservice.util.WalletAddress
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
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

@Disabled // TODO fix in SD-708
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

        val snapshotRepository = mock<SnapshotRepository>()
        val issuerAddress = ContractAddress("abcd")
        val payoutBlock = BlockNumber(BigInteger.TEN)
        val ignoredAddresses = setOf(WalletAddress("dead"))
        val taskUuid = UUID.randomUUID()
        val params = CreateSnapshotParams(
            chainId = chainId,
            name = "", // TODO fix in SD-708
            assetAddress = assetAddress,
            ownerAddress = requesterAddress,
            payoutBlock = payoutBlock,
            ignoredHolderAddresses = ignoredAddresses
        )

        suppose("payout task is created in database") {
            given(snapshotRepository.createSnapshot(params))
                .willReturn(taskUuid)
        }

        suppose("pending task will be returned") {
            given(snapshotRepository.getPending())
                .willReturn(
                    PendingSnapshot(
                        id = taskUuid,
                        name = "", // TODO fix in SD-708
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredHolderAddresses = ignoredAddresses,
                        ownerAddress = requesterAddress
                    )
                )
        }

        val accountBalances = listOf(
            AccountBalance(WalletAddress("2"), Balance(BigInteger.ONE)),
            AccountBalance(WalletAddress("3"), Balance(BigInteger.TWO))
        )
        val totalAssetAmount = Balance(BigInteger("3"))

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
            snapshotRepository = snapshotRepository,
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
            given(snapshotRepository.getById(taskUuid))
                .willReturn(
                    Snapshot(
                        id = taskUuid,
                        name = "", // TODO fix in SD-708
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredHolderAddresses = ignoredAddresses,
                        ownerAddress = requesterAddress,
                        data = SuccessfulSnapshotData(
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
                    FullCreatePayoutTask(
                        taskId = taskUuid,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        payoutBlockNumber = payoutBlock,
                        ignoredAssetAddresses = ignoredAddresses,
                        requesterAddress = requesterAddress,
                        issuerAddress = issuerAddress,
                        snapshotStatus = SnapshotStatus.SUCCESS,
                        data = FullCreatePayoutData(
                            totalAssetAmount = totalAssetAmount,
                            merkleRootHash = tree.root.hash,
                            merkleTreeIpfsHash = ipfsHash,
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
            verifyMock(snapshotRepository)
                .createSnapshot(params)

            // processTasks()
            verifyMock(snapshotRepository)
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

            verifyMock(snapshotRepository)
                .completeSnapshot(taskUuid, treeUuid, ipfsHash, totalAssetAmount)

            // getTaskById()
            verifyMock(snapshotRepository)
                .getById(taskUuid)
            verifyNoMoreInteractions(snapshotRepository)

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

        val snapshotRepository = mock<SnapshotRepository>()
        val issuerAddress = ContractAddress("abcd")
        val payoutBlock = BlockNumber(BigInteger.TEN)
        val ignoredAddresses = setOf(WalletAddress("dead"))
        val taskUuid = UUID.randomUUID()
        val params = CreateSnapshotParams(
            chainId = chainId,
            name = "", // TODO fix in SD-708
            assetAddress = assetAddress,
            ownerAddress = requesterAddress,
            payoutBlock = payoutBlock,
            ignoredHolderAddresses = ignoredAddresses
        )

        suppose("payout task is created in database") {
            given(snapshotRepository.createSnapshot(params))
                .willReturn(taskUuid)
        }

        suppose("pending task will be returned") {
            given(snapshotRepository.getPending())
                .willReturn(
                    PendingSnapshot(
                        id = taskUuid,
                        name = "", // TODO fix in SD-708
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredHolderAddresses = ignoredAddresses,
                        ownerAddress = requesterAddress
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
            snapshotRepository = snapshotRepository,
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
            given(snapshotRepository.getById(taskUuid))
                .willReturn(
                    Snapshot(
                        id = taskUuid,
                        name = "", // TODO fix in SD-708
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredHolderAddresses = ignoredAddresses,
                        ownerAddress = requesterAddress,
                        data = OtherSnapshotData(SnapshotStatus.FAILED)
                    )
                )
        }

        verify("task execution failed") {
            val response = service.getTaskById(taskUuid)

            assertThat(response).withMessage()
                .isEqualTo(
                    FullCreatePayoutTask(
                        taskId = taskUuid,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        payoutBlockNumber = payoutBlock,
                        ignoredAssetAddresses = ignoredAddresses,
                        requesterAddress = requesterAddress,
                        issuerAddress = issuerAddress,
                        snapshotStatus = SnapshotStatus.FAILED,
                        data = null
                    )
                )
        }

        verify("correct service and repository calls are made") {
            // submitTask()
            verifyMock(blockchainService)
                .getAssetOwner(chainId, assetAddress)
            verifyMock(snapshotRepository)
                .createSnapshot(params)

            // processTasks()
            verifyMock(snapshotRepository)
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

            verifyMock(snapshotRepository)
                .failSnapshot(taskUuid)

            // getTaskById()
            verifyMock(snapshotRepository)
                .getById(taskUuid)
            verifyNoMoreInteractions(snapshotRepository)

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

        val snapshotRepository = mock<SnapshotRepository>()
        val issuerAddress = ContractAddress("abcd")
        val payoutBlock = BlockNumber(BigInteger.TEN)
        val ignoredAddresses = setOf(WalletAddress("dead"))
        val taskUuid = UUID.randomUUID()
        val params = CreateSnapshotParams(
            chainId = chainId,
            name = "", // TODO fix in SD-708
            assetAddress = assetAddress,
            ownerAddress = requesterAddress,
            payoutBlock = payoutBlock,
            ignoredHolderAddresses = ignoredAddresses
        )

        suppose("payout task is created in database") {
            given(snapshotRepository.createSnapshot(params))
                .willReturn(taskUuid)
        }

        suppose("pending task will be returned") {
            given(snapshotRepository.getPending())
                .willReturn(
                    PendingSnapshot(
                        id = taskUuid,
                        name = "", // TODO fix in SD-708
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredHolderAddresses = ignoredAddresses,
                        ownerAddress = requesterAddress
                    )
                )
        }

        val accountBalances = listOf(
            AccountBalance(WalletAddress("2"), Balance(BigInteger.ONE)),
            AccountBalance(WalletAddress("3"), Balance(BigInteger.TWO))
        )
        val totalAssetAmount = Balance(BigInteger("3"))

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
            snapshotRepository = snapshotRepository,
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
            given(snapshotRepository.getById(taskUuid))
                .willReturn(
                    Snapshot(
                        id = taskUuid,
                        name = "", // TODO fix in SD-708
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredHolderAddresses = ignoredAddresses,
                        ownerAddress = requesterAddress,
                        data = SuccessfulSnapshotData(
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
                    FullCreatePayoutTask(
                        taskId = taskUuid,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        payoutBlockNumber = payoutBlock,
                        ignoredAssetAddresses = ignoredAddresses,
                        requesterAddress = requesterAddress,
                        issuerAddress = issuerAddress,
                        snapshotStatus = SnapshotStatus.SUCCESS,
                        data = FullCreatePayoutData(
                            totalAssetAmount = totalAssetAmount,
                            merkleRootHash = tree.root.hash,
                            merkleTreeIpfsHash = ipfsHash,
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
            verifyMock(snapshotRepository)
                .createSnapshot(params)

            // processTasks()
            verifyMock(snapshotRepository)
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

            verifyMock(snapshotRepository)
                .completeSnapshot(taskUuid, treeUuid, ipfsHash, totalAssetAmount)

            // getTaskById()
            verifyMock(snapshotRepository)
                .getById(taskUuid)
            verifyNoMoreInteractions(snapshotRepository)

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

        val snapshotRepository = mock<SnapshotRepository>()
        val issuerAddress = ContractAddress("abcd")
        val payoutBlock = BlockNumber(BigInteger.TEN)
        val ignoredAddresses = setOf(WalletAddress("dead"))
        val params = CreateSnapshotParams(
            chainId = chainId,
            name = "", // TODO fix in SD-708
            assetAddress = assetAddress,
            ownerAddress = requesterAddress,
            payoutBlock = payoutBlock,
            ignoredHolderAddresses = ignoredAddresses
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
            snapshotRepository = snapshotRepository,
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
            verifyNoInteractions(snapshotRepository)
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
        val params = CreateSnapshotParams(
            chainId = chainId,
            name = "", // TODO fix in SD-708
            assetAddress = assetAddress,
            ownerAddress = requesterAddress,
            payoutBlock = payoutBlock,
            ignoredHolderAddresses = ignoredAddresses
        )

        val snapshotRepository = mock<SnapshotRepository>()

        suppose("payout task is created in database") {
            given(snapshotRepository.createSnapshot(params))
                .willReturn(taskUuid)
        }

        suppose("pending task will be returned") {
            given(snapshotRepository.getPending())
                .willReturn(
                    PendingSnapshot(
                        id = taskUuid,
                        name = "", // TODO fix in SD-708
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredHolderAddresses = ignoredAddresses,
                        ownerAddress = requesterAddress
                    )
                )
        }

        val accountBalances = listOf(
            AccountBalance(WalletAddress("2"), Balance(BigInteger.ONE)),
            AccountBalance(WalletAddress("3"), Balance(BigInteger.TWO))
        )
        val totalAssetAmount = Balance(BigInteger("3"))

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
            snapshotRepository = snapshotRepository,
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
            given(snapshotRepository.getById(taskUuid))
                .willReturn(
                    Snapshot(
                        id = taskUuid,
                        name = "", // TODO fix in SD-708
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredHolderAddresses = ignoredAddresses,
                        ownerAddress = requesterAddress,
                        data = SuccessfulSnapshotData(
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
                    FullCreatePayoutTask(
                        taskId = taskUuid,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        payoutBlockNumber = payoutBlock,
                        ignoredAssetAddresses = ignoredAddresses,
                        requesterAddress = requesterAddress,
                        issuerAddress = issuerAddress,
                        snapshotStatus = SnapshotStatus.SUCCESS,
                        data = FullCreatePayoutData(
                            totalAssetAmount = totalAssetAmount,
                            merkleRootHash = tree.root.hash,
                            merkleTreeIpfsHash = ipfsHash,
                            merkleTreeDepth = tree.root.depth,
                            hashFn = tree.hashFn
                        )
                    )
                )
        }

        verify("correct service and repository calls are made") {
            // submitTask()
            verifyMock(snapshotRepository)
                .createSnapshot(params)

            // processTasks()
            verifyMock(snapshotRepository)
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

            verifyMock(snapshotRepository)
                .completeSnapshot(taskUuid, treeUuid, ipfsHash, totalAssetAmount)

            // getTaskById()
            verifyMock(snapshotRepository)
                .getById(taskUuid)
            verifyNoMoreInteractions(snapshotRepository)

            verifyMock(merkleTreeRepository)
                .getById(treeUuid)
            verifyNoMoreInteractions(merkleTreeRepository)
        }
    }

    @Test
    fun mustCorrectlyFetchAllTasksByChainIdIssuerOwnerAndStatuses() {
        val merkleTreeRepository = mock<MerkleTreeRepository>()
        val treeUuid = UUID.randomUUID()
        val tree = MerkleTree(
            listOf(AccountBalance(WalletAddress("aaaa"), Balance(BigInteger.ONE))),
            HashFunction.KECCAK_256
        )

        suppose("Merkle tree can be fetched by ID") {
            given(merkleTreeRepository.getById(treeUuid))
                .willReturn(tree)
        }

        val snapshotRepository = mock<SnapshotRepository>()
        val chainId = ChainId(1L)
        val issuer = ContractAddress("a")
        val owner = WalletAddress("b")
        val ipfsHash = IpfsHash("ipfs-hash")
        val totalAssetAmount = Balance(BigInteger("1000"))
        val tasks = listOf(
            Snapshot(
                id = UUID.randomUUID(),
                name = "", // TODO fix in SD-708
                chainId = chainId,
                assetAddress = ContractAddress("1"),
                blockNumber = BlockNumber(BigInteger.TEN),
                ignoredHolderAddresses = emptySet(),
                ownerAddress = owner,
                data = SuccessfulSnapshotData(
                    merkleTreeRootId = treeUuid,
                    merkleTreeIpfsHash = ipfsHash,
                    totalAssetAmount = totalAssetAmount
                )
            ),
            Snapshot(
                id = UUID.randomUUID(),
                name = "", // TODO fix in SD-708
                chainId = chainId,
                assetAddress = ContractAddress("2"),
                blockNumber = BlockNumber(BigInteger.TEN),
                ignoredHolderAddresses = emptySet(),
                ownerAddress = owner,
                data = OtherSnapshotData(SnapshotStatus.PENDING)
            )
        )
        val statuses = setOf(SnapshotStatus.PENDING, SnapshotStatus.SUCCESS)

        suppose("some create payout tasks are returned") {
            given(snapshotRepository.getAllByChainIdOwnerAndStatuses(chainId, owner, statuses))
                .willReturn(tasks)
        }

        val executorServiceProvider = mock<ScheduledExecutorServiceProvider>()
        val scheduler = ManualFixedScheduler()

        suppose("ManualFixedScheduler will be used") {
            given(executorServiceProvider.newSingleThreadScheduledExecutor(any()))
                .willReturn(scheduler)
        }

        val service = CreatePayoutQueueServiceImpl(
            merkleTreeRepository = merkleTreeRepository,
            snapshotRepository = snapshotRepository,
            ipfsService = mock(),
            blockchainService = mock(),
            applicationProperties = ApplicationProperties(),
            scheduledExecutorServiceProvider = executorServiceProvider
        )

        verify("task are correctly fetched by issuer and owner") {
            val response = service.getAllTasksByIssuerAndOwner(chainId, issuer, owner, statuses)

            assertThat(response).withMessage()
                .containsExactlyInAnyOrder(
                    FullCreatePayoutTask(
                        taskId = tasks[0].id,
                        chainId = tasks[0].chainId,
                        assetAddress = tasks[0].assetAddress,
                        payoutBlockNumber = tasks[0].blockNumber,
                        ignoredAssetAddresses = emptySet(),
                        requesterAddress = tasks[0].ownerAddress,
                        issuerAddress = null, // TODO fix in SD-708
                        snapshotStatus = SnapshotStatus.SUCCESS,
                        data = FullCreatePayoutData(
                            totalAssetAmount = totalAssetAmount,
                            merkleRootHash = tree.root.hash,
                            merkleTreeIpfsHash = ipfsHash,
                            merkleTreeDepth = tree.root.depth,
                            hashFn = tree.hashFn
                        )
                    ),
                    FullCreatePayoutTask(
                        taskId = tasks[1].id,
                        chainId = tasks[1].chainId,
                        assetAddress = tasks[1].assetAddress,
                        payoutBlockNumber = tasks[1].blockNumber,
                        ignoredAssetAddresses = emptySet(),
                        requesterAddress = tasks[1].ownerAddress,
                        issuerAddress = null, // TODO fix in SD-708
                        snapshotStatus = SnapshotStatus.PENDING,
                        data = null
                    )
                )
        }
    }
}
