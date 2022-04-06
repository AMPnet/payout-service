package com.ampnet.payoutservice.controller

import com.ampnet.payoutservice.TestBase
import com.ampnet.payoutservice.blockchain.BlockchainService
import com.ampnet.payoutservice.controller.response.InvestorPayoutResponse
import com.ampnet.payoutservice.controller.response.InvestorPayoutsResponse
import com.ampnet.payoutservice.model.params.FetchMerkleTreeParams
import com.ampnet.payoutservice.model.params.GetPayoutsForInvestorParams
import com.ampnet.payoutservice.model.result.MerkleTreeWithId
import com.ampnet.payoutservice.model.result.Payout
import com.ampnet.payoutservice.model.result.PayoutForInvestor
import com.ampnet.payoutservice.repository.MerkleTreeRepository
import com.ampnet.payoutservice.util.AccountBalance
import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.Hash
import com.ampnet.payoutservice.util.HashFunction
import com.ampnet.payoutservice.util.IpfsHash
import com.ampnet.payoutservice.util.MerkleTree
import com.ampnet.payoutservice.util.WalletAddress
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.springframework.http.ResponseEntity
import java.math.BigInteger
import java.util.UUID

class PayoutControllerTest : TestBase() {

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

        val controller = PayoutController(blockchainService, merkleTreeRepository)

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
                                    payout = payouts[0].toPayoutResponse(),
                                    investor = params.investor.rawValue,
                                    amountClaimed = BigInteger.ZERO,
                                    amountClaimable = BigInteger("100"),
                                    balance = accountBalances[0].balance.rawValue,
                                    path = trees[0].pathTo(accountBalances[0])!!
                                ),
                                InvestorPayoutResponse(
                                    payout = payouts[1].toPayoutResponse(),
                                    investor = params.investor.rawValue,
                                    amountClaimed = BigInteger("400"),
                                    amountClaimable = BigInteger.ZERO,
                                    balance = accountBalances[1].balance.rawValue,
                                    path = trees[1].pathTo(accountBalances[1])!!
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

        val controller = PayoutController(blockchainService, merkleTreeRepository)

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
                                    payout = payouts[0].toPayoutResponse(),
                                    investor = params.investor.rawValue,
                                    amountClaimed = BigInteger.ZERO,
                                    amountClaimable = BigInteger("100"),
                                    balance = accountBalances[0].balance.rawValue,
                                    path = trees[0].pathTo(accountBalances[0])!!
                                ),
                                InvestorPayoutResponse(
                                    payout = payouts[1].toPayoutResponse(),
                                    investor = params.investor.rawValue,
                                    amountClaimed = BigInteger("400"),
                                    amountClaimable = BigInteger.ZERO,
                                    balance = accountBalances[1].balance.rawValue,
                                    path = trees[1].pathTo(accountBalances[1])!!
                                ) // payouts[2] is not claimable by this investor
                            )
                        )
                    )
                )
        }
    }

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
