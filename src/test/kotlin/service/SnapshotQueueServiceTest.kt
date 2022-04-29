package service

import com.ampnet.payoutservice.ManualFixedScheduler
import com.ampnet.payoutservice.TestBase
import com.ampnet.payoutservice.blockchain.BlockchainService
import com.ampnet.payoutservice.config.ApplicationProperties
import com.ampnet.payoutservice.exception.InvalidRequestException
import com.ampnet.payoutservice.model.params.CreateSnapshotParams
import com.ampnet.payoutservice.model.params.FetchMerkleTreeParams
import com.ampnet.payoutservice.model.result.FullSnapshot
import com.ampnet.payoutservice.model.result.FullSnapshotData
import com.ampnet.payoutservice.model.result.MerkleTreeWithId
import com.ampnet.payoutservice.model.result.OtherSnapshotData
import com.ampnet.payoutservice.model.result.PendingSnapshot
import com.ampnet.payoutservice.model.result.Snapshot
import com.ampnet.payoutservice.model.result.SuccessfulSnapshotData
import com.ampnet.payoutservice.repository.MerkleTreeRepository
import com.ampnet.payoutservice.repository.SnapshotRepository
import com.ampnet.payoutservice.service.IpfsService
import com.ampnet.payoutservice.service.ScheduledExecutorServiceProvider
import com.ampnet.payoutservice.service.SnapshotQueueServiceImpl
import com.ampnet.payoutservice.util.AccountBalance
import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.HashFunction
import com.ampnet.payoutservice.util.IpfsHash
import com.ampnet.payoutservice.util.MerkleTree
import com.ampnet.payoutservice.util.SnapshotFailureCause
import com.ampnet.payoutservice.util.SnapshotStatus
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

class SnapshotQueueServiceTest : TestBase() {

    @Test
    fun mustCorrectlySubmitAndCreateSnapshotWhenMerkleTreeDoesNotAlreadyExist() {
        val blockchainService = mock<BlockchainService>()
        val chainId = ChainId(1L)
        val assetAddress = ContractAddress("a")
        val ownerAddress = WalletAddress("1")

        suppose("requesting user is asset owner") {
            given(blockchainService.getAssetOwner(chainId, assetAddress))
                .willReturn(ownerAddress)
        }

        val snapshotRepository = mock<SnapshotRepository>()
        val payoutBlock = BlockNumber(BigInteger.TEN)
        val ignoredHolderAddresses = setOf(WalletAddress("dead"))
        val snapshotUuid = UUID.randomUUID()
        val name = "snapshot-name"
        val params = CreateSnapshotParams(
            chainId = chainId,
            name = name,
            assetAddress = assetAddress,
            ownerAddress = ownerAddress,
            payoutBlock = payoutBlock,
            ignoredHolderAddresses = ignoredHolderAddresses
        )

        suppose("snapshot is created in database") {
            given(snapshotRepository.createSnapshot(params))
                .willReturn(snapshotUuid)
        }

        suppose("pending snapshot will be returned") {
            given(snapshotRepository.getPending())
                .willReturn(
                    PendingSnapshot(
                        id = snapshotUuid,
                        name = name,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredHolderAddresses = ignoredHolderAddresses,
                        ownerAddress = ownerAddress
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
                    ignoredErc20Addresses = ignoredHolderAddresses,
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

        val service = SnapshotQueueServiceImpl(
            merkleTreeRepository = merkleTreeRepository,
            snapshotRepository = snapshotRepository,
            ipfsService = ipfsService,
            blockchainService = blockchainService,
            applicationProperties = properties,
            scheduledExecutorServiceProvider = executorServiceProvider
        )

        verify("snapshot is submitted and correct snapshot ID is returned") {
            val response = service.submitSnapshot(params)

            assertThat(response).withMessage()
                .isEqualTo(snapshotUuid)
        }

        suppose("snapshot is processed") {
            scheduler.execute()
        }

        suppose("successful snapshot is returned from database") {
            given(snapshotRepository.getById(snapshotUuid))
                .willReturn(
                    Snapshot(
                        id = snapshotUuid,
                        name = name,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredHolderAddresses = ignoredHolderAddresses,
                        ownerAddress = ownerAddress,
                        data = SuccessfulSnapshotData(
                            merkleTreeRootId = treeUuid,
                            merkleTreeIpfsHash = ipfsHash,
                            totalAssetAmount = totalAssetAmount
                        )
                    )
                )
        }

        verify("snapshot is successfully processed") {
            val response = service.getSnapshotById(snapshotUuid)

            assertThat(response).withMessage()
                .isEqualTo(
                    FullSnapshot(
                        id = snapshotUuid,
                        name = name,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredHolderAddresses = ignoredHolderAddresses,
                        ownerAddress = ownerAddress,
                        snapshotStatus = SnapshotStatus.SUCCESS,
                        snapshotFailureCause = null,
                        data = FullSnapshotData(
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
            // submitSnapshot()
            verifyMock(blockchainService)
                .getAssetOwner(chainId, assetAddress)
            verifyMock(snapshotRepository)
                .createSnapshot(params)

            // processSnapshots()
            verifyMock(snapshotRepository)
                .getPending()

            // handlePendingSnapshot()
            verifyMock(blockchainService)
                .fetchErc20AccountBalances(
                    chainId = chainId,
                    erc20ContractAddress = assetAddress,
                    ignoredErc20Addresses = ignoredHolderAddresses,
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
                .completeSnapshot(snapshotUuid, treeUuid, ipfsHash, totalAssetAmount)

            // getSnapshotById()
            verifyMock(snapshotRepository)
                .getById(snapshotUuid)
            verifyNoMoreInteractions(snapshotRepository)

            verifyMock(merkleTreeRepository)
                .getById(treeUuid)
            verifyNoMoreInteractions(merkleTreeRepository)
        }
    }

    @Test
    fun mustFailSnapshotWhenExceptionIsThrownDuringProcessing() {
        val blockchainService = mock<BlockchainService>()
        val chainId = ChainId(1L)
        val assetAddress = ContractAddress("a")
        val ownerAddress = WalletAddress("1")

        suppose("requesting user is asset owner") {
            given(blockchainService.getAssetOwner(chainId, assetAddress))
                .willReturn(ownerAddress)
        }

        val snapshotRepository = mock<SnapshotRepository>()
        val payoutBlock = BlockNumber(BigInteger.TEN)
        val ignoredHolderAddresses = setOf(WalletAddress("dead"))
        val snapshotUuid = UUID.randomUUID()
        val name = "snapshot-name"
        val params = CreateSnapshotParams(
            chainId = chainId,
            name = name,
            assetAddress = assetAddress,
            ownerAddress = ownerAddress,
            payoutBlock = payoutBlock,
            ignoredHolderAddresses = ignoredHolderAddresses
        )

        suppose("snapshot is created in database") {
            given(snapshotRepository.createSnapshot(params))
                .willReturn(snapshotUuid)
        }

        suppose("pending snapshot will be returned") {
            given(snapshotRepository.getPending())
                .willReturn(
                    PendingSnapshot(
                        id = snapshotUuid,
                        name = name,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredHolderAddresses = ignoredHolderAddresses,
                        ownerAddress = ownerAddress
                    )
                )
        }

        suppose("fetching asset balances throws exception") {
            given(
                blockchainService.fetchErc20AccountBalances(
                    chainId = chainId,
                    erc20ContractAddress = assetAddress,
                    ignoredErc20Addresses = ignoredHolderAddresses,
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

        val service = SnapshotQueueServiceImpl(
            merkleTreeRepository = merkleTreeRepository,
            snapshotRepository = snapshotRepository,
            ipfsService = ipfsService,
            blockchainService = blockchainService,
            applicationProperties = properties,
            scheduledExecutorServiceProvider = executorServiceProvider
        )

        verify("snapshot is submitted and correct snapshot ID is returned") {
            val response = service.submitSnapshot(params)

            assertThat(response).withMessage()
                .isEqualTo(snapshotUuid)
        }

        suppose("snapshot is processed") {
            scheduler.execute()
        }

        suppose("failed snapshot is returned from database") {
            given(snapshotRepository.getById(snapshotUuid))
                .willReturn(
                    Snapshot(
                        id = snapshotUuid,
                        name = name,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredHolderAddresses = ignoredHolderAddresses,
                        ownerAddress = ownerAddress,
                        data = OtherSnapshotData(SnapshotStatus.FAILED, SnapshotFailureCause.OTHER)
                    )
                )
        }

        verify("snapshot processing failed") {
            val response = service.getSnapshotById(snapshotUuid)

            assertThat(response).withMessage()
                .isEqualTo(
                    FullSnapshot(
                        id = snapshotUuid,
                        name = name,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredHolderAddresses = ignoredHolderAddresses,
                        ownerAddress = ownerAddress,
                        snapshotStatus = SnapshotStatus.FAILED,
                        snapshotFailureCause = SnapshotFailureCause.OTHER,
                        data = null
                    )
                )
        }

        verify("correct service and repository calls are made") {
            // submitSnapshot()
            verifyMock(blockchainService)
                .getAssetOwner(chainId, assetAddress)
            verifyMock(snapshotRepository)
                .createSnapshot(params)

            // processSnapshots()
            verifyMock(snapshotRepository)
                .getPending()

            // handlePendingSnapshot()
            verifyMock(blockchainService)
                .fetchErc20AccountBalances(
                    chainId = chainId,
                    erc20ContractAddress = assetAddress,
                    ignoredErc20Addresses = ignoredHolderAddresses,
                    startBlock = null,
                    endBlock = payoutBlock
                )
            verifyNoMoreInteractions(blockchainService)

            verifyMock(snapshotRepository)
                .failSnapshot(snapshotUuid, SnapshotFailureCause.OTHER)

            // getSnapshotById()
            verifyMock(snapshotRepository)
                .getById(snapshotUuid)
            verifyNoMoreInteractions(snapshotRepository)

            verifyNoInteractions(merkleTreeRepository)
            verifyNoInteractions(ipfsService)
        }
    }

    @Test
    fun mustFailSnapshotWhenExceptionWithExceededLogSizeLimitIsThrownDuringProcessing() {
        val blockchainService = mock<BlockchainService>()
        val chainId = ChainId(1L)
        val assetAddress = ContractAddress("a")
        val ownerAddress = WalletAddress("1")

        suppose("requesting user is asset owner") {
            given(blockchainService.getAssetOwner(chainId, assetAddress))
                .willReturn(ownerAddress)
        }

        val snapshotRepository = mock<SnapshotRepository>()
        val payoutBlock = BlockNumber(BigInteger.TEN)
        val ignoredHolderAddresses = setOf(WalletAddress("dead"))
        val snapshotUuid = UUID.randomUUID()
        val name = "snapshot-name"
        val params = CreateSnapshotParams(
            chainId = chainId,
            name = name,
            assetAddress = assetAddress,
            ownerAddress = ownerAddress,
            payoutBlock = payoutBlock,
            ignoredHolderAddresses = ignoredHolderAddresses
        )

        suppose("snapshot is created in database") {
            given(snapshotRepository.createSnapshot(params))
                .willReturn(snapshotUuid)
        }

        suppose("pending snapshot will be returned") {
            given(snapshotRepository.getPending())
                .willReturn(
                    PendingSnapshot(
                        id = snapshotUuid,
                        name = name,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredHolderAddresses = ignoredHolderAddresses,
                        ownerAddress = ownerAddress
                    )
                )
        }

        suppose("fetching asset balances throws exception") {
            given(
                blockchainService.fetchErc20AccountBalances(
                    chainId = chainId,
                    erc20ContractAddress = assetAddress,
                    ignoredErc20Addresses = ignoredHolderAddresses,
                    startBlock = null,
                    endBlock = payoutBlock
                )
            ).willThrow(RuntimeException(RuntimeException("Log response size exceeded")))
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

        val service = SnapshotQueueServiceImpl(
            merkleTreeRepository = merkleTreeRepository,
            snapshotRepository = snapshotRepository,
            ipfsService = ipfsService,
            blockchainService = blockchainService,
            applicationProperties = properties,
            scheduledExecutorServiceProvider = executorServiceProvider
        )

        verify("snapshot is submitted and correct snapshot ID is returned") {
            val response = service.submitSnapshot(params)

            assertThat(response).withMessage()
                .isEqualTo(snapshotUuid)
        }

        suppose("snapshot is processed") {
            scheduler.execute()
        }

        suppose("failed snapshot is returned from database") {
            given(snapshotRepository.getById(snapshotUuid))
                .willReturn(
                    Snapshot(
                        id = snapshotUuid,
                        name = name,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredHolderAddresses = ignoredHolderAddresses,
                        ownerAddress = ownerAddress,
                        data = OtherSnapshotData(SnapshotStatus.FAILED, SnapshotFailureCause.LOG_RESPONSE_LIMIT)
                    )
                )
        }

        verify("snapshot processing failed") {
            val response = service.getSnapshotById(snapshotUuid)

            assertThat(response).withMessage()
                .isEqualTo(
                    FullSnapshot(
                        id = snapshotUuid,
                        name = name,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredHolderAddresses = ignoredHolderAddresses,
                        ownerAddress = ownerAddress,
                        snapshotStatus = SnapshotStatus.FAILED,
                        snapshotFailureCause = SnapshotFailureCause.LOG_RESPONSE_LIMIT,
                        data = null
                    )
                )
        }

        verify("correct service and repository calls are made") {
            // submitSnapshot()
            verifyMock(blockchainService)
                .getAssetOwner(chainId, assetAddress)
            verifyMock(snapshotRepository)
                .createSnapshot(params)

            // processSnapshots()
            verifyMock(snapshotRepository)
                .getPending()

            // handlePendingSnapshot()
            verifyMock(blockchainService)
                .fetchErc20AccountBalances(
                    chainId = chainId,
                    erc20ContractAddress = assetAddress,
                    ignoredErc20Addresses = ignoredHolderAddresses,
                    startBlock = null,
                    endBlock = payoutBlock
                )
            verifyNoMoreInteractions(blockchainService)

            verifyMock(snapshotRepository)
                .failSnapshot(snapshotUuid, SnapshotFailureCause.LOG_RESPONSE_LIMIT)

            // getSnapshotById()
            verifyMock(snapshotRepository)
                .getById(snapshotUuid)
            verifyNoMoreInteractions(snapshotRepository)

            verifyNoInteractions(merkleTreeRepository)
            verifyNoInteractions(ipfsService)
        }
    }

    @Test
    fun mustCorrectlySubmitAndCreateSnapshotWhenMerkleTreeAlreadyExist() {
        val blockchainService = mock<BlockchainService>()
        val chainId = ChainId(1L)
        val assetAddress = ContractAddress("a")
        val ownerAddress = WalletAddress("1")

        suppose("requesting user is asset owner") {
            given(blockchainService.getAssetOwner(chainId, assetAddress))
                .willReturn(ownerAddress)
        }

        val snapshotRepository = mock<SnapshotRepository>()
        val payoutBlock = BlockNumber(BigInteger.TEN)
        val ignoredHolderAddresses = setOf(WalletAddress("dead"))
        val snapshotUuid = UUID.randomUUID()
        val name = "snapshot-name"
        val params = CreateSnapshotParams(
            chainId = chainId,
            name = name,
            assetAddress = assetAddress,
            ownerAddress = ownerAddress,
            payoutBlock = payoutBlock,
            ignoredHolderAddresses = ignoredHolderAddresses
        )

        suppose("snapshot is created in database") {
            given(snapshotRepository.createSnapshot(params))
                .willReturn(snapshotUuid)
        }

        suppose("pending snapshot will be returned") {
            given(snapshotRepository.getPending())
                .willReturn(
                    PendingSnapshot(
                        id = snapshotUuid,
                        name = name,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredHolderAddresses = ignoredHolderAddresses,
                        ownerAddress = ownerAddress
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
                    ignoredErc20Addresses = ignoredHolderAddresses,
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

        val service = SnapshotQueueServiceImpl(
            merkleTreeRepository = merkleTreeRepository,
            snapshotRepository = snapshotRepository,
            ipfsService = ipfsService,
            blockchainService = blockchainService,
            applicationProperties = properties,
            scheduledExecutorServiceProvider = executorServiceProvider
        )

        verify("snapshot is submitted and correct snapshot ID is returned") {
            val response = service.submitSnapshot(params)

            assertThat(response).withMessage()
                .isEqualTo(snapshotUuid)
        }

        suppose("snapshot is processed") {
            scheduler.execute()
        }

        suppose("successful snapshot is returned from database") {
            given(snapshotRepository.getById(snapshotUuid))
                .willReturn(
                    Snapshot(
                        id = snapshotUuid,
                        name = name,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredHolderAddresses = ignoredHolderAddresses,
                        ownerAddress = ownerAddress,
                        data = SuccessfulSnapshotData(
                            merkleTreeRootId = treeUuid,
                            merkleTreeIpfsHash = ipfsHash,
                            totalAssetAmount = totalAssetAmount
                        )
                    )
                )
        }

        verify("snapshot is successfully processed") {
            val response = service.getSnapshotById(snapshotUuid)

            assertThat(response).withMessage()
                .isEqualTo(
                    FullSnapshot(
                        id = snapshotUuid,
                        name = name,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredHolderAddresses = ignoredHolderAddresses,
                        ownerAddress = ownerAddress,
                        snapshotStatus = SnapshotStatus.SUCCESS,
                        snapshotFailureCause = null,
                        data = FullSnapshotData(
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
            // submitSnapshot()
            verifyMock(blockchainService)
                .getAssetOwner(chainId, assetAddress)
            verifyMock(snapshotRepository)
                .createSnapshot(params)

            // processSnapshots()
            verifyMock(snapshotRepository)
                .getPending()

            // handlePendingSnapshot()
            verifyMock(blockchainService)
                .fetchErc20AccountBalances(
                    chainId = chainId,
                    erc20ContractAddress = assetAddress,
                    ignoredErc20Addresses = ignoredHolderAddresses,
                    startBlock = null,
                    endBlock = payoutBlock
                )
            verifyNoMoreInteractions(blockchainService)

            verifyMock(merkleTreeRepository)
                .fetchTree(FetchMerkleTreeParams(tree.root.hash, chainId, assetAddress))

            verifyMock(ipfsService).pinJsonToIpfs(tree)
            verifyNoMoreInteractions(ipfsService)

            verifyMock(snapshotRepository)
                .completeSnapshot(snapshotUuid, treeUuid, ipfsHash, totalAssetAmount)

            // getSnapshotById()
            verifyMock(snapshotRepository)
                .getById(snapshotUuid)
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
        val ownerAddress = WalletAddress("1")

        suppose("requesting user is not asset owner") {
            given(blockchainService.getAssetOwner(chainId, assetAddress))
                .willReturn(WalletAddress("2"))
        }

        val properties = suppose("asset owner will be checked") {
            ApplicationProperties().apply { payout.checkAssetOwner = true }
        }

        val snapshotRepository = mock<SnapshotRepository>()
        val payoutBlock = BlockNumber(BigInteger.TEN)
        val ignoredHolderAddresses = setOf(WalletAddress("dead"))
        val name = "snapshot-name"
        val params = CreateSnapshotParams(
            chainId = chainId,
            name = name,
            assetAddress = assetAddress,
            ownerAddress = ownerAddress,
            payoutBlock = payoutBlock,
            ignoredHolderAddresses = ignoredHolderAddresses
        )
        val executorServiceProvider = mock<ScheduledExecutorServiceProvider>()
        val scheduler = ManualFixedScheduler()

        suppose("ManualFixedScheduler will be used") {
            given(executorServiceProvider.newSingleThreadScheduledExecutor(any()))
                .willReturn(scheduler)
        }

        val merkleTreeRepository = mock<MerkleTreeRepository>()
        val ipfsService = mock<IpfsService>()

        val service = SnapshotQueueServiceImpl(
            merkleTreeRepository = merkleTreeRepository,
            snapshotRepository = snapshotRepository,
            ipfsService = ipfsService,
            blockchainService = blockchainService,
            applicationProperties = properties,
            scheduledExecutorServiceProvider = executorServiceProvider
        )

        verify("InvalidRequestException exception is thrown") {
            assertThrows<InvalidRequestException>(message) {
                service.submitSnapshot(params)
            }
        }

        verify("correct service and repository calls are made") {
            // submitSnapshot()
            verifyMock(blockchainService)
                .getAssetOwner(chainId, assetAddress)

            verifyNoMoreInteractions(blockchainService)
            verifyNoInteractions(merkleTreeRepository)
            verifyNoInteractions(snapshotRepository)
            verifyNoInteractions(ipfsService)
        }
    }

    @Test
    fun mustCorrectlySubmitCreateSnapshotForNonOwnerWhenAssetOwnerIsNotChecked() {
        val chainId = ChainId(1L)
        val assetAddress = ContractAddress("a")
        val ownerAddress = WalletAddress("1")
        val payoutBlock = BlockNumber(BigInteger.TEN)
        val ignoredHolderAddresses = setOf(WalletAddress("dead"))
        val snapshotUuid = UUID.randomUUID()
        val name = "snapshot-name"
        val params = CreateSnapshotParams(
            chainId = chainId,
            name = name,
            assetAddress = assetAddress,
            ownerAddress = ownerAddress,
            payoutBlock = payoutBlock,
            ignoredHolderAddresses = ignoredHolderAddresses
        )

        val snapshotRepository = mock<SnapshotRepository>()

        suppose("snapshot is created in database") {
            given(snapshotRepository.createSnapshot(params))
                .willReturn(snapshotUuid)
        }

        suppose("pending snapshot will be returned") {
            given(snapshotRepository.getPending())
                .willReturn(
                    PendingSnapshot(
                        id = snapshotUuid,
                        name = name,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredHolderAddresses = ignoredHolderAddresses,
                        ownerAddress = ownerAddress
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
                    ignoredErc20Addresses = ignoredHolderAddresses,
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

        val service = SnapshotQueueServiceImpl(
            merkleTreeRepository = merkleTreeRepository,
            snapshotRepository = snapshotRepository,
            ipfsService = ipfsService,
            blockchainService = blockchainService,
            applicationProperties = properties,
            scheduledExecutorServiceProvider = executorServiceProvider
        )

        verify("snapshot is submitted and correct snapshot ID is returned") {
            val response = service.submitSnapshot(params)

            assertThat(response).withMessage()
                .isEqualTo(snapshotUuid)
        }

        suppose("snapshot is processed") {
            scheduler.execute()
        }

        suppose("successful snapshot is returned from database") {
            given(snapshotRepository.getById(snapshotUuid))
                .willReturn(
                    Snapshot(
                        id = snapshotUuid,
                        name = name,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredHolderAddresses = ignoredHolderAddresses,
                        ownerAddress = ownerAddress,
                        data = SuccessfulSnapshotData(
                            merkleTreeRootId = treeUuid,
                            merkleTreeIpfsHash = ipfsHash,
                            totalAssetAmount = totalAssetAmount
                        )
                    )
                )
        }

        verify("snapshot is successfully processed") {
            val response = service.getSnapshotById(snapshotUuid)

            assertThat(response).withMessage()
                .isEqualTo(
                    FullSnapshot(
                        id = snapshotUuid,
                        name = name,
                        chainId = chainId,
                        assetAddress = assetAddress,
                        blockNumber = payoutBlock,
                        ignoredHolderAddresses = ignoredHolderAddresses,
                        ownerAddress = ownerAddress,
                        snapshotStatus = SnapshotStatus.SUCCESS,
                        snapshotFailureCause = null,
                        data = FullSnapshotData(
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
            // submitSnapshot()
            verifyMock(snapshotRepository)
                .createSnapshot(params)

            // processSnapshots()
            verifyMock(snapshotRepository)
                .getPending()

            // handlePendingSnapshot()
            verifyMock(blockchainService)
                .fetchErc20AccountBalances(
                    chainId = chainId,
                    erc20ContractAddress = assetAddress,
                    ignoredErc20Addresses = ignoredHolderAddresses,
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
                .completeSnapshot(snapshotUuid, treeUuid, ipfsHash, totalAssetAmount)

            // getSnapshotById()
            verifyMock(snapshotRepository)
                .getById(snapshotUuid)
            verifyNoMoreInteractions(snapshotRepository)

            verifyMock(merkleTreeRepository)
                .getById(treeUuid)
            verifyNoMoreInteractions(merkleTreeRepository)
        }
    }

    @Test
    fun mustCorrectlyFetchAllSnapshotsByChainIdOwnerAndStatuses() {
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
        val owner = WalletAddress("b")
        val ipfsHash = IpfsHash("ipfs-hash")
        val totalAssetAmount = Balance(BigInteger("1000"))
        val snapshots = listOf(
            Snapshot(
                id = UUID.randomUUID(),
                name = "snapshot-1",
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
                name = "snapshot-2",
                chainId = chainId,
                assetAddress = ContractAddress("2"),
                blockNumber = BlockNumber(BigInteger.TEN),
                ignoredHolderAddresses = emptySet(),
                ownerAddress = owner,
                data = OtherSnapshotData(SnapshotStatus.PENDING, null)
            )
        )
        val statuses = setOf(SnapshotStatus.PENDING, SnapshotStatus.SUCCESS)

        suppose("some snapshots are returned") {
            given(snapshotRepository.getAllByChainIdOwnerAndStatuses(chainId, owner, statuses))
                .willReturn(snapshots)
        }

        val executorServiceProvider = mock<ScheduledExecutorServiceProvider>()
        val scheduler = ManualFixedScheduler()

        suppose("ManualFixedScheduler will be used") {
            given(executorServiceProvider.newSingleThreadScheduledExecutor(any()))
                .willReturn(scheduler)
        }

        val service = SnapshotQueueServiceImpl(
            merkleTreeRepository = merkleTreeRepository,
            snapshotRepository = snapshotRepository,
            ipfsService = mock(),
            blockchainService = mock(),
            applicationProperties = ApplicationProperties(),
            scheduledExecutorServiceProvider = executorServiceProvider
        )

        verify("snapshots are correctly fetched by chainId and owner") {
            val response = service.getAllSnapshotsByChainIdOwnerAndStatuses(chainId, owner, statuses)

            assertThat(response).withMessage()
                .containsExactlyInAnyOrder(
                    FullSnapshot(
                        id = snapshots[0].id,
                        name = snapshots[0].name,
                        chainId = snapshots[0].chainId,
                        assetAddress = snapshots[0].assetAddress,
                        blockNumber = snapshots[0].blockNumber,
                        ignoredHolderAddresses = emptySet(),
                        ownerAddress = snapshots[0].ownerAddress,
                        snapshotStatus = SnapshotStatus.SUCCESS,
                        snapshotFailureCause = null,
                        data = FullSnapshotData(
                            totalAssetAmount = totalAssetAmount,
                            merkleRootHash = tree.root.hash,
                            merkleTreeIpfsHash = ipfsHash,
                            merkleTreeDepth = tree.root.depth,
                            hashFn = tree.hashFn
                        )
                    ),
                    FullSnapshot(
                        id = snapshots[1].id,
                        name = snapshots[1].name,
                        chainId = snapshots[1].chainId,
                        assetAddress = snapshots[1].assetAddress,
                        blockNumber = snapshots[1].blockNumber,
                        ignoredHolderAddresses = emptySet(),
                        ownerAddress = snapshots[1].ownerAddress,
                        snapshotStatus = SnapshotStatus.PENDING,
                        snapshotFailureCause = null,
                        data = null
                    )
                )
        }
    }
}
