package com.ampnet.payoutservice.controller

import com.ampnet.payoutservice.TestBase
import com.ampnet.payoutservice.blockchain.BlockchainService
import com.ampnet.payoutservice.controller.request.CreatePayoutRequest
import com.ampnet.payoutservice.controller.response.AdminPayoutsResponse
import com.ampnet.payoutservice.controller.response.CreatePayoutResponse
import com.ampnet.payoutservice.controller.response.InvestorPayoutResponse
import com.ampnet.payoutservice.controller.response.InvestorPayoutsResponse
import com.ampnet.payoutservice.exception.ResourceNotFoundException
import com.ampnet.payoutservice.model.params.CreateSnapshotParams
import com.ampnet.payoutservice.model.params.FetchMerkleTreeParams
import com.ampnet.payoutservice.model.params.GetPayoutsForAdminParams
import com.ampnet.payoutservice.model.params.GetPayoutsForInvestorParams
import com.ampnet.payoutservice.model.result.FullSnapshot
import com.ampnet.payoutservice.model.result.FullSnapshotData
import com.ampnet.payoutservice.model.result.MerkleTreeWithId
import com.ampnet.payoutservice.model.result.Payout
import com.ampnet.payoutservice.model.result.PayoutForInvestor
import com.ampnet.payoutservice.repository.MerkleTreeRepository
import com.ampnet.payoutservice.service.SnapshotQueueService
import com.ampnet.payoutservice.util.AccountBalance
import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.Hash
import com.ampnet.payoutservice.util.HashFunction
import com.ampnet.payoutservice.util.IpfsHash
import com.ampnet.payoutservice.util.MerkleTree
import com.ampnet.payoutservice.util.PayoutStatus
import com.ampnet.payoutservice.util.SnapshotStatus
import com.ampnet.payoutservice.util.WalletAddress
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.springframework.http.ResponseEntity
import java.math.BigInteger
import java.util.UUID

@Disabled // TODO will be fixed in SD-709
class PayoutControllerTest : TestBase() {

    @Test
    fun mustCorrectlyFetchPayoutTaskById() {
        val taskUuid = UUID.randomUUID()
        val task = FullSnapshot(
            id = taskUuid,
            name = "", // TODO in SD-709
            chainId = ChainId(1L),
            assetAddress = ContractAddress("a"),
            payoutBlockNumber = BlockNumber(BigInteger.ONE),
            ignoredHolderAddresses = setOf(WalletAddress("b")),
            ownerAddress = WalletAddress("c"),
            snapshotStatus = SnapshotStatus.PENDING,
            data = null
        )

        val service = mock<SnapshotQueueService>()

        suppose("some task will be returned") {
            given(service.getSnapshotById(taskUuid))
                .willReturn(task)
        }

        val controller = PayoutController(service, mock(), mock())

        verify("correct response is returned") {
            val controllerResponse = controller.getPayoutByTaskId(1L, taskUuid)

            assertThat(controllerResponse).withMessage()
                .isEqualTo(ResponseEntity.ok(task)) // TODO .toPayoutResponse()))
        }

        verify("exception is thrown for wrong chain ID") {
            assertThrows<ResourceNotFoundException>(message) {
                controller.getPayoutByTaskId(123L, taskUuid)
            }
        }
    }

    @Test
    fun mustThrowResourceNotFoundExceptionForNonExistentPayoutTask() {
        val service = mock<SnapshotQueueService>()

        suppose("null will be returned") {
            given(service.getSnapshotById(any()))
                .willReturn(null)
        }

        val controller = PayoutController(service, mock(), mock())

        verify("exception is thrown") {
            assertThrows<ResourceNotFoundException>(message) {
                controller.getPayoutByTaskId(1L, UUID.randomUUID())
            }
        }
    }

    @Test
    fun mustCorrectlyFetchPayoutsForAdminWithNullIssuerOwnerAndStatus() {
        val params = GetPayoutsForAdminParams(
            chainId = ChainId(123L),
            issuer = null,
            assetFactories = listOf(ContractAddress("a"), ContractAddress("b")),
            payoutService = ContractAddress("c"),
            payoutManager = ContractAddress("d"),
            owner = null
        )

        val payouts = listOf(
            createPayout(0, Hash("0"), BigInteger.ONE, BigInteger.TWO), // matched with payoutTasks[0]
            createPayout(1, Hash("0"), BigInteger.ONE, BigInteger.TWO), // not matched with any complete task
            createPayout(2, Hash("0"), BigInteger.ONE, BigInteger.TWO), // not matched with any complete task
        )
        val blockchainService = mock<BlockchainService>()

        suppose("some payouts will be returned") {
            given(blockchainService.getPayoutsForAdmin(params))
                .willReturn(payouts)
        }

        val payoutTasks = listOf(
            createSuccessfulFullCreatePayoutTask(payouts[0], null), // successful, matched with payouts[0]
            createSuccessfulFullCreatePayoutTask(payouts[1], null).copy( // successful, not matched with any payout
                assetAddress = ContractAddress("ffff")
            ),
            createPendingFullCreatePayoutTask(payouts[2], null) // pending
        )
        val queueService = mock<SnapshotQueueService>()

        suppose("some payout tasks will be returned") {
            given(
                queueService.getAllSnapshotsByChainIdOwnerAndStatuses(
                    chainId = params.chainId,
                    owner = null,
                    statuses = emptySet()
                )
            )
                .willReturn(payoutTasks)
        }

        val controller = PayoutController(queueService, blockchainService, mock())

        verify("correct payouts are returned") {
            val result = controller.getPayouts(
                chainId = params.chainId.value,
                assetFactories = params.assetFactories.map { it.rawValue },
                payoutService = params.payoutService.rawValue,
                payoutManager = params.payoutManager.rawValue,
                issuer = null,
                owner = null,
                status = null
            )

            assertThat(result).withMessage()
                .isEqualTo(
                    ResponseEntity.ok(
                        AdminPayoutsResponse(
                            listOf(
                                payouts[0].toPayoutResponse(taskId = payoutTasks[0].id, issuer = null),
                                // TODO payoutTasks[1].toPayoutResponse(),
                                payouts[1].toPayoutResponse(taskId = null, issuer = null),
                                payouts[2].toPayoutResponse(taskId = null, issuer = null),
                                // TODO payoutTasks[2].toPayoutResponse()
                            )
                        )
                    )
                )
        }
    }

    @Test
    fun mustCorrectlyFetchPayoutsForAdminWithSomeIssuerAndOwner() {
        val issuer = ContractAddress("abc123")
        val owner = WalletAddress("def456")
        val params = GetPayoutsForAdminParams(
            chainId = ChainId(123L),
            issuer = issuer,
            assetFactories = listOf(ContractAddress("a"), ContractAddress("b")),
            payoutService = ContractAddress("c"),
            payoutManager = ContractAddress("d"),
            owner = owner
        )

        val payouts = listOf(
            createPayout(0, Hash("0"), BigInteger.ONE, BigInteger.TWO), // matched with payoutTasks[0]
            createPayout(1, Hash("0"), BigInteger.ONE, BigInteger.TWO), // not matched with any complete task
            createPayout(2, Hash("0"), BigInteger.ONE, BigInteger.TWO), // not matched with any complete task
        )
        val blockchainService = mock<BlockchainService>()

        suppose("some payouts will be returned") {
            given(blockchainService.getPayoutsForAdmin(params))
                .willReturn(payouts)
        }

        val payoutTasks = listOf(
            createSuccessfulFullCreatePayoutTask(payouts[0], issuer), // successful, matched with payouts[0]
            createSuccessfulFullCreatePayoutTask(
                payouts[1],
                issuer
            ).copy(assetAddress = ContractAddress("ffff")), // successful, not matched with any payout
            createPendingFullCreatePayoutTask(payouts[2], issuer) // pending
        )
        val queueService = mock<SnapshotQueueService>()

        suppose("some payout tasks will be returned") {
            given(
                queueService.getAllSnapshotsByChainIdOwnerAndStatuses(
                    chainId = params.chainId,
                    owner = owner,
                    statuses = emptySet()
                )
            )
                .willReturn(payoutTasks)
        }

        val controller = PayoutController(queueService, blockchainService, mock())

        verify("correct payouts are returned") {
            val result = controller.getPayouts(
                chainId = params.chainId.value,
                assetFactories = params.assetFactories.map { it.rawValue },
                payoutService = params.payoutService.rawValue,
                payoutManager = params.payoutManager.rawValue,
                issuer = issuer.rawValue,
                owner = owner.rawValue,
                status = null
            )

            assertThat(result).withMessage()
                .isEqualTo(
                    ResponseEntity.ok(
                        AdminPayoutsResponse(
                            listOf(
                                payouts[0].toPayoutResponse(taskId = payoutTasks[0].id, issuer = issuer.rawValue),
                                // TODO payoutTasks[1].toPayoutResponse(),
                                payouts[1].toPayoutResponse(taskId = null, issuer = issuer.rawValue),
                                payouts[2].toPayoutResponse(taskId = null, issuer = issuer.rawValue),
                                // TODO payoutTasks[2].toPayoutResponse()
                            )
                        )
                    )
                )
        }
    }

    @Test
    fun mustCorrectlyFetchPayoutsForAdminWithSomeStatusFilter() {
        val issuer = ContractAddress("abc123")
        val owner = WalletAddress("def456")
        val status: List<PayoutStatus> = listOf(
            PayoutStatus.PAYOUT_CREATED,
            PayoutStatus.PROOF_CREATED,
            PayoutStatus.PROOF_PENDING
        )
        val params = GetPayoutsForAdminParams(
            chainId = ChainId(123L),
            issuer = issuer,
            assetFactories = listOf(ContractAddress("a"), ContractAddress("b")),
            payoutService = ContractAddress("c"),
            payoutManager = ContractAddress("d"),
            owner = owner
        )

        val payouts = listOf(
            createPayout(0, Hash("0"), BigInteger.ONE, BigInteger.TWO), // matched with payoutTasks[0]
            createPayout(1, Hash("0"), BigInteger.ONE, BigInteger.TWO), // not matched with any complete task
            createPayout(2, Hash("0"), BigInteger.ONE, BigInteger.TWO), // not matched with any complete task
        )
        val blockchainService = mock<BlockchainService>()

        suppose("some payouts will be returned") {
            given(blockchainService.getPayoutsForAdmin(params))
                .willReturn(payouts)
        }

        val payoutTasks = listOf(
            createSuccessfulFullCreatePayoutTask(payouts[0], issuer), // successful, matched with payouts[0]
            createSuccessfulFullCreatePayoutTask(
                payouts[1],
                issuer
            ).copy(assetAddress = ContractAddress("ffff")), // successful, not matched with any payout
            createPendingFullCreatePayoutTask(payouts[2], issuer) // pending
        )
        val queueService = mock<SnapshotQueueService>()

        suppose("some payout tasks will be returned") {
            given(
                queueService.getAllSnapshotsByChainIdOwnerAndStatuses(
                    chainId = params.chainId,
                    owner = owner,
                    statuses = setOf(SnapshotStatus.SUCCESS, SnapshotStatus.PENDING)
                )
            )
                .willReturn(payoutTasks)
        }

        val controller = PayoutController(queueService, blockchainService, mock())

        verify("correct payouts are returned") {
            val result = controller.getPayouts(
                chainId = params.chainId.value,
                assetFactories = params.assetFactories.map { it.rawValue },
                payoutService = params.payoutService.rawValue,
                payoutManager = params.payoutManager.rawValue,
                issuer = issuer.rawValue,
                owner = owner.rawValue,
                status = status
            )

            assertThat(result).withMessage()
                .isEqualTo(
                    ResponseEntity.ok(
                        AdminPayoutsResponse(
                            listOf(
                                payouts[0].toPayoutResponse(taskId = payoutTasks[0].id, issuer = issuer.rawValue),
                                // TODO payoutTasks[1].toPayoutResponse(),
                                payouts[1].toPayoutResponse(taskId = null, issuer = issuer.rawValue),
                                payouts[2].toPayoutResponse(taskId = null, issuer = issuer.rawValue),
                                // TODO payoutTasks[2].toPayoutResponse()
                            )
                        )
                    )
                )
        }
    }

    @Test
    fun mustCorrectlyFetchPayoutsForInvestorWithNullIssuer() {
        val params = GetPayoutsForInvestorParams(
            chainId = ChainId(123L),
            issuer = null,
            assetFactories = listOf(ContractAddress("a"), ContractAddress("b")),
            payoutService = ContractAddress("c"),
            payoutManager = ContractAddress("d"),
            investor = WalletAddress("1")
        )
        val accountBalances = listOf(
            AccountBalance(params.investor, Balance(BigInteger("100"))),
            AccountBalance(params.investor, Balance(BigInteger("200"))),
            AccountBalance(WalletAddress("2"), Balance(BigInteger("300")))
        )
        val trees = listOf(
            MerkleTree(listOf(accountBalances[0]), HashFunction.KECCAK_256),
            MerkleTree(listOf(accountBalances[1]), HashFunction.KECCAK_256),
            MerkleTree(listOf(accountBalances[2]), HashFunction.KECCAK_256)
        )
        val payouts = listOf(
            createPayout(0, trees[0].root.hash, asset = BigInteger("1000"), reward = BigInteger("1000")),
            createPayout(1, trees[1].root.hash, asset = BigInteger("2000"), reward = BigInteger("4000")),
            createPayout(2, trees[2].root.hash, asset = BigInteger("3000"), reward = BigInteger("9000"))
        )
        val payoutsForInvestor = listOf(
            PayoutForInvestor(
                payout = payouts[0],
                investor = params.investor,
                amountClaimed = Balance(BigInteger.ZERO) // not claimed
            ),
            PayoutForInvestor(
                payout = payouts[1],
                investor = params.investor,
                amountClaimed = Balance(BigInteger("400")) // fully claimed, 10% of 4000 (200 / 2000 * 4000)
            ),
            PayoutForInvestor(
                payout = payouts[2],
                investor = params.investor,
                amountClaimed = Balance(BigInteger.ZERO) // not claimable at all for this investor
            )
        )
        val blockchainService = mock<BlockchainService>()

        suppose("some payouts are returned for investor") {
            given(blockchainService.getPayoutsForInvestor(params))
                .willReturn(payoutsForInvestor)
        }

        val merkleTreeRepository = mock<MerkleTreeRepository>()

        suppose("some Merkle trees will be returned") {
            given(
                merkleTreeRepository.fetchTree(
                    FetchMerkleTreeParams(trees[0].root.hash, params.chainId, payouts[0].asset)
                )
            ).willReturn(MerkleTreeWithId(UUID.randomUUID(), trees[0]))

            given(
                merkleTreeRepository.fetchTree(
                    FetchMerkleTreeParams(trees[1].root.hash, params.chainId, payouts[1].asset)
                )
            ).willReturn(MerkleTreeWithId(UUID.randomUUID(), trees[1]))

            given(
                merkleTreeRepository.fetchTree(
                    FetchMerkleTreeParams(trees[2].root.hash, params.chainId, payouts[2].asset)
                )
            ).willReturn(MerkleTreeWithId(UUID.randomUUID(), trees[2]))
        }

        val controller = PayoutController(mock(), blockchainService, merkleTreeRepository)

        verify("correct investor payout states are returned") {
            val result = controller.getPayoutsForInvestor(
                chainId = params.chainId.value,
                investorAddress = params.investor.rawValue,
                assetFactories = params.assetFactories.map { it.rawValue },
                payoutService = params.payoutService.rawValue,
                payoutManager = params.payoutManager.rawValue,
                issuer = params.issuer?.rawValue
            )

            assertThat(result).withMessage()
                .isEqualTo(
                    ResponseEntity.ok(
                        InvestorPayoutsResponse(
                            listOf(
                                InvestorPayoutResponse(
                                    payout = payouts[0].toPayoutResponse(
                                        taskId = null,
                                        issuer = params.issuer?.rawValue
                                    ),
                                    investor = params.investor.rawValue,
                                    amountClaimed = BigInteger.ZERO,
                                    amountClaimable = BigInteger("100"),
                                    balance = BigInteger("100"),
                                    path = trees[0].pathTo(accountBalances[0])
                                ),
                                InvestorPayoutResponse(
                                    payout = payouts[1].toPayoutResponse(
                                        taskId = null,
                                        issuer = params.issuer?.rawValue
                                    ),
                                    investor = params.investor.rawValue,
                                    amountClaimed = BigInteger("400"),
                                    amountClaimable = null,
                                    balance = null,
                                    path = null
                                ) // payouts[2] is not claimable by this investor
                            )
                        )
                    )
                )
        }
    }

    @Test
    fun mustCorrectlyFetchPayoutsForInvestorWithSomeIssuer() {
        val params = GetPayoutsForInvestorParams(
            chainId = ChainId(123L),
            issuer = ContractAddress("abcd"),
            assetFactories = listOf(ContractAddress("a"), ContractAddress("b")),
            payoutService = ContractAddress("c"),
            payoutManager = ContractAddress("d"),
            investor = WalletAddress("1")
        )
        val accountBalances = listOf(
            AccountBalance(params.investor, Balance(BigInteger("100"))),
            AccountBalance(params.investor, Balance(BigInteger("200"))),
            AccountBalance(WalletAddress("2"), Balance(BigInteger("300")))
        )
        val trees = listOf(
            MerkleTree(listOf(accountBalances[0]), HashFunction.KECCAK_256),
            MerkleTree(listOf(accountBalances[1]), HashFunction.KECCAK_256),
            MerkleTree(listOf(accountBalances[2]), HashFunction.KECCAK_256)
        )
        val payouts = listOf(
            createPayout(0, trees[0].root.hash, asset = BigInteger("1000"), reward = BigInteger("1000")),
            createPayout(1, trees[1].root.hash, asset = BigInteger("2000"), reward = BigInteger("4000")),
            createPayout(2, trees[2].root.hash, asset = BigInteger("3000"), reward = BigInteger("9000"))
        )
        val payoutsForInvestor = listOf(
            PayoutForInvestor(
                payout = payouts[0],
                investor = params.investor,
                amountClaimed = Balance(BigInteger.ZERO) // not claimed
            ),
            PayoutForInvestor(
                payout = payouts[1],
                investor = params.investor,
                amountClaimed = Balance(BigInteger("400")) // fully claimed, 10% of 4000 (200 / 2000 * 4000)
            ),
            PayoutForInvestor(
                payout = payouts[2],
                investor = params.investor,
                amountClaimed = Balance(BigInteger.ZERO) // not claimable at all for this investor
            )
        )
        val blockchainService = mock<BlockchainService>()

        suppose("some payouts are returned for investor") {
            given(blockchainService.getPayoutsForInvestor(params))
                .willReturn(payoutsForInvestor)
        }

        val merkleTreeRepository = mock<MerkleTreeRepository>()

        suppose("some Merkle trees will be returned") {
            given(
                merkleTreeRepository.fetchTree(
                    FetchMerkleTreeParams(trees[0].root.hash, params.chainId, payouts[0].asset)
                )
            ).willReturn(MerkleTreeWithId(UUID.randomUUID(), trees[0]))

            given(
                merkleTreeRepository.fetchTree(
                    FetchMerkleTreeParams(trees[1].root.hash, params.chainId, payouts[1].asset)
                )
            ).willReturn(MerkleTreeWithId(UUID.randomUUID(), trees[1]))

            given(
                merkleTreeRepository.fetchTree(
                    FetchMerkleTreeParams(trees[2].root.hash, params.chainId, payouts[2].asset)
                )
            ).willReturn(MerkleTreeWithId(UUID.randomUUID(), trees[2]))
        }

        val controller = PayoutController(mock(), blockchainService, merkleTreeRepository)

        verify("correct investor payout states are returned") {
            val result = controller.getPayoutsForInvestor(
                chainId = params.chainId.value,
                investorAddress = params.investor.rawValue,
                assetFactories = params.assetFactories.map { it.rawValue },
                payoutService = params.payoutService.rawValue,
                payoutManager = params.payoutManager.rawValue,
                issuer = params.issuer?.rawValue
            )

            assertThat(result).withMessage()
                .isEqualTo(
                    ResponseEntity.ok(
                        InvestorPayoutsResponse(
                            listOf(
                                InvestorPayoutResponse(
                                    payout = payouts[0].toPayoutResponse(
                                        taskId = null,
                                        issuer = params.issuer?.rawValue
                                    ),
                                    investor = params.investor.rawValue,
                                    amountClaimed = BigInteger.ZERO,
                                    amountClaimable = BigInteger("100"),
                                    balance = BigInteger("100"),
                                    path = trees[0].pathTo(accountBalances[0])
                                ),
                                InvestorPayoutResponse(
                                    payout = payouts[1].toPayoutResponse(
                                        taskId = null,
                                        issuer = params.issuer?.rawValue
                                    ),
                                    investor = params.investor.rawValue,
                                    amountClaimed = BigInteger("400"),
                                    amountClaimable = null,
                                    balance = null,
                                    path = null
                                ) // payouts[2] is not claimable by this investor
                            )
                        )
                    )
                )
        }
    }

    @Test
    fun mustCorrectlyCreatePayoutTaskAndReturnAResponse() {
        val service = mock<SnapshotQueueService>()
        val chainId = ChainId(1L)
        val assetAddress = ContractAddress("a")
        val requesterAddress = WalletAddress("b")
        val requestBody = CreatePayoutRequest(
            payoutBlockNumber = BigInteger.TEN,
            ignoredHolderAddresses = setOf("f"),
            issuerAddress = "c"
        )
        val taskUuid = UUID.randomUUID()

        suppose("create payout task will be submitted") {
            given(
                service.submitSnapshot(
                    CreateSnapshotParams(
                        chainId = chainId,
                        name = "", // TODO sd-709
                        assetAddress = assetAddress,
                        ownerAddress = requesterAddress,
                        payoutBlock = BlockNumber(requestBody.payoutBlockNumber),
                        ignoredHolderAddresses = requestBody.ignoredHolderAddresses
                            .mapTo(HashSet()) { WalletAddress(it) }
                    )
                )
            )
                .willReturn(taskUuid)
        }

        val controller = PayoutController(service, mock(), mock())

        verify("correct response is returned") {
            val controllerResponse = controller.createPayout(
                chainId = chainId.value,
                assetAddress = assetAddress.rawValue,
                requestBody = requestBody,
                requesterAddress = requesterAddress.rawValue
            )

            assertThat(controllerResponse).withMessage()
                .isEqualTo(ResponseEntity.ok(CreatePayoutResponse(taskUuid)))
        }
    }

    private fun createPendingFullCreatePayoutTask(payout: Payout, issuer: ContractAddress?): FullSnapshot =
        FullSnapshot(
            id = UUID.randomUUID(),
            name = "", // TODO in SD-709
            chainId = ChainId(123L),
            assetAddress = payout.asset,
            payoutBlockNumber = payout.assetSnapshotBlockNumber,
            ignoredHolderAddresses = payout.ignoredAssetAddresses,
            ownerAddress = payout.payoutOwner,
            snapshotStatus = SnapshotStatus.PENDING,
            data = null
        )

    private fun createSuccessfulFullCreatePayoutTask(payout: Payout, issuer: ContractAddress?): FullSnapshot =
        createPendingFullCreatePayoutTask(payout, issuer).copy(
            snapshotStatus = SnapshotStatus.SUCCESS,
            data = FullSnapshotData(
                totalAssetAmount = payout.totalAssetAmount,
                merkleRootHash = payout.assetSnapshotMerkleRoot,
                merkleTreeIpfsHash = payout.assetSnapshotMerkleIpfsHash,
                merkleTreeDepth = payout.assetSnapshotMerkleDepth.intValueExact(),
                hashFn = HashFunction.KECCAK_256
            )
        )

    private fun createPayout(id: Long, rootHash: Hash, asset: BigInteger, reward: BigInteger): Payout =
        Payout(
            payoutId = BigInteger.valueOf(id),
            payoutOwner = WalletAddress("aaa$id"),
            payoutInfo = "payout-info-$id",
            isCanceled = false,
            asset = ContractAddress("bbb$id"),
            totalAssetAmount = Balance(asset),
            ignoredAssetAddresses = emptySet(),
            assetSnapshotMerkleRoot = rootHash,
            assetSnapshotMerkleDepth = BigInteger.valueOf(id),
            assetSnapshotBlockNumber = BlockNumber(BigInteger.valueOf(id * 100)),
            assetSnapshotMerkleIpfsHash = IpfsHash("ipfs-hash-$id"),
            rewardAsset = ContractAddress("ccc$id"),
            totalRewardAmount = Balance(reward),
            remainingRewardAmount = Balance(reward)
        )
}
