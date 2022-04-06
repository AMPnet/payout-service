package com.ampnet.payoutservice.controller

import com.ampnet.payoutservice.blockchain.BlockchainService
import com.ampnet.payoutservice.controller.response.InvestorPayoutResponse
import com.ampnet.payoutservice.controller.response.InvestorPayoutsResponse
import com.ampnet.payoutservice.model.params.FetchMerkleTreeParams
import com.ampnet.payoutservice.model.params.GetPayoutsForInvestorParams
import com.ampnet.payoutservice.repository.MerkleTreeRepository
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.WalletAddress
import mu.KLogging
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class PayoutController(
    private val blockchainService: BlockchainService,
    private val merkleTreeRepository: MerkleTreeRepository
) {

    companion object : KLogging()

    @Suppress("LongParameterList")
    @GetMapping("/claimable_payouts")
    fun getPayoutsForInvestor(
        @RequestParam(required = true) chainId: Long,
        @RequestParam(required = true) assetFactories: List<String>,
        @RequestParam(required = true) payoutService: String,
        @RequestParam(required = true) payoutManager: String,
        @RequestParam(required = false) issuer: String?,
        @AuthenticationPrincipal investorAddress: String
    ): ResponseEntity<InvestorPayoutsResponse> {
        logger.debug {
            "Get investor payouts, chainId: $chainId, investorAddress: $investorAddress, issuer: $issuer," +
                " assetFactories: $assetFactories, payoutService: $payoutService, payoutManager: $payoutManager"
        }
        val chainIdValue = ChainId(chainId)
        val payouts = blockchainService.getPayoutsForInvestor(
            GetPayoutsForInvestorParams(
                chainId = chainIdValue,
                issuer = issuer?.let { ContractAddress(it) },
                assetFactories = assetFactories.map { ContractAddress(it) },
                payoutService = ContractAddress(payoutService),
                payoutManager = ContractAddress(payoutManager),
                investor = WalletAddress(investorAddress)
            )
        )
        val merkleTreeParams = payouts.mapTo(HashSet()) {
            FetchMerkleTreeParams(it.payout.assetSnapshotMerkleRoot, chainIdValue, it.payout.asset)
        }
        val merkleTrees = merkleTreeParams.mapNotNull { merkleTreeRepository.fetchTree(it)?.tree }
            .associateBy { it.root.hash }

        val investorPayouts = payouts.mapNotNull { payoutData ->
            val tree = merkleTrees[payoutData.payout.assetSnapshotMerkleRoot]
            val accountBalance = tree?.leafNodesByAddress?.get(payoutData.investor)?.value?.data
            val path = accountBalance?.let { tree.pathTo(it) }

            if (path != null) { // return only claimable (and already claimed) payouts for this investor
                val totalRewardAmount = payoutData.payout.totalRewardAmount.rawValue
                val balance = accountBalance.balance.rawValue
                val totalAssetAmount = payoutData.payout.totalAssetAmount.rawValue
                val totalAmountClaimable = (totalRewardAmount * balance) / totalAssetAmount
                val amountClaimable = totalAmountClaimable - payoutData.amountClaimed.rawValue

                val payout = payoutData.payout.toPayoutResponse()

                InvestorPayoutResponse(
                    payout = payout,
                    investor = payoutData.investor.rawValue,
                    amountClaimed = payoutData.amountClaimed.rawValue,

                    amountClaimable = amountClaimable,
                    balance = accountBalance.balance.rawValue,
                    path = path
                )
            } else null
        }

        return ResponseEntity.ok(InvestorPayoutsResponse(investorPayouts))
    }
}
