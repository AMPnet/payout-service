package com.ampnet.payoutservice.controller

import com.ampnet.payoutservice.blockchain.BlockchainService
import com.ampnet.payoutservice.controller.request.CreatePayoutRequest
import com.ampnet.payoutservice.controller.response.AdminPayoutsResponse
import com.ampnet.payoutservice.controller.response.CreatePayoutResponse
import com.ampnet.payoutservice.controller.response.InvestorPayoutResponse
import com.ampnet.payoutservice.controller.response.InvestorPayoutsResponse
import com.ampnet.payoutservice.controller.response.PayoutResponse
import com.ampnet.payoutservice.exception.ErrorCode
import com.ampnet.payoutservice.exception.ResourceNotFoundException
import com.ampnet.payoutservice.model.params.CreatePayoutTaskParams
import com.ampnet.payoutservice.model.params.FetchMerkleTreeParams
import com.ampnet.payoutservice.model.params.GetPayoutsForAdminParams
import com.ampnet.payoutservice.model.params.GetPayoutsForInvestorParams
import com.ampnet.payoutservice.model.result.FullCreatePayoutTask
import com.ampnet.payoutservice.model.result.Payout
import com.ampnet.payoutservice.repository.MerkleTreeRepository
import com.ampnet.payoutservice.service.CreatePayoutQueueService
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.PayoutStatus
import com.ampnet.payoutservice.util.TaskStatus
import com.ampnet.payoutservice.util.WalletAddress
import mu.KLogging
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigInteger
import java.util.UUID

@RestController
class PayoutController(
    private val createPayoutQueueService: CreatePayoutQueueService,
    private val blockchainService: BlockchainService,
    private val merkleTreeRepository: MerkleTreeRepository
) {

    companion object : KLogging()

    @GetMapping("/payouts/{chainId}/task/{taskId}")
    fun getPayoutByTaskId(
        @PathVariable chainId: Long,
        @PathVariable taskId: UUID
    ): ResponseEntity<PayoutResponse> {
        logger.debug { "Get payout task by chainId: $chainId, taskId: $taskId" }
        return createPayoutQueueService.getTaskById(taskId)
            ?.takeIf { it.chainId.value == chainId }
            ?.let { ResponseEntity.ok(it.toPayoutResponse()) }
            ?: throw ResourceNotFoundException(
                ErrorCode.PAYOUT_TASK_NOT_FOUND,
                "Create payout task not found"
            )
    }

    @Suppress("LongParameterList")
    @GetMapping("/payouts/{chainId}")
    fun getPayouts(
        @PathVariable chainId: Long,
        @RequestParam(required = true) assetFactories: List<String>,
        @RequestParam(required = true) payoutService: String,
        @RequestParam(required = true) payoutManager: String,
        @RequestParam(required = false) issuer: String?,
        @RequestParam(required = false) owner: String?,
        @RequestParam(required = false) status: List<PayoutStatus>?
    ): ResponseEntity<AdminPayoutsResponse> {
        logger.debug {
            "Get admin payouts, chainId: $chainId, issuer: $issuer, owner: $owner, statuses: $status," +
                " assetFactories: $assetFactories, payoutService: $payoutService, payoutManager: $payoutManager"
        }
        val chainIdValue = ChainId(chainId)
        val issuerAddress = issuer?.let { ContractAddress(it) }
        val ownerAddress = owner?.let { WalletAddress(it) }
        val statuses = status ?: emptyList()

        val payoutTasks = createPayoutQueueService.getAllTasksByIssuerAndOwner(
            chainId = chainIdValue,
            issuer = issuerAddress,
            owner = ownerAddress,
            statuses = statuses.mapTo(HashSet()) { it.toTaskStatus }
        )
        val (successfulTasks, otherTasks) = payoutTasks.partition { it.taskStatus == TaskStatus.SUCCESS }
        val createdPayouts = if (statuses.isEmpty() || statuses.contains(PayoutStatus.PAYOUT_CREATED)) {
            blockchainService.getPayoutsForAdmin(
                GetPayoutsForAdminParams(
                    chainId = chainIdValue,
                    issuer = issuerAddress,
                    assetFactories = assetFactories.map { ContractAddress(it) },
                    payoutService = ContractAddress(payoutService),
                    payoutManager = ContractAddress(payoutManager),
                    owner = ownerAddress
                )
            )
        } else emptyList()

        val otherPayouts = otherTasks.map { it.toPayoutResponse() }
        val allPayouts = matchCreatedPayoutsWithSuccessfulTasks(successfulTasks, createdPayouts, issuer) + otherPayouts

        return ResponseEntity.ok(AdminPayoutsResponse(allPayouts))
    }

    @Suppress("LongParameterList")
    @GetMapping("/payouts/{chainId}/investor/{investorAddress}")
    fun getPayoutsForInvestor(
        @PathVariable chainId: Long,
        @PathVariable investorAddress: String,
        @RequestParam(required = true) assetFactories: List<String>,
        @RequestParam(required = true) payoutService: String,
        @RequestParam(required = true) payoutManager: String,
        @RequestParam(required = false) issuer: String?
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

                val payout = payoutData.payout.toPayoutResponse(taskId = null, issuer = issuer)

                InvestorPayoutResponse(
                    payout = payout,
                    investor = payoutData.investor.rawValue,
                    amountClaimed = payoutData.amountClaimed.rawValue,

                    amountClaimable = if (amountClaimable > BigInteger.ZERO) amountClaimable else null,
                    balance = if (amountClaimable > BigInteger.ZERO) accountBalance.balance.rawValue else null,
                    path = if (amountClaimable > BigInteger.ZERO) path else null
                )
            } else null
        }

        return ResponseEntity.ok(InvestorPayoutsResponse(investorPayouts))
    }

    @PostMapping("/payouts/{chainId}/{assetAddress}")
    fun createPayout(
        @PathVariable chainId: Long,
        @PathVariable assetAddress: String,
        @RequestBody requestBody: CreatePayoutRequest,
        @AuthenticationPrincipal requesterAddress: String
    ): ResponseEntity<CreatePayoutResponse> {
        logger.debug {
            "Request payout creation, chainId: $chainId, assetAddress: $assetAddress, requestBody: $requestBody," +
                " requesterAddress: $requesterAddress"
        }
        val taskId = createPayoutQueueService.submitTask(
            CreatePayoutTaskParams(
                chainId = ChainId(chainId),
                assetAddress = ContractAddress(assetAddress),
                requesterAddress = WalletAddress(requesterAddress),
                issuerAddress = requestBody.issuerAddress?.let { ContractAddress(it) },
                payoutBlock = BlockNumber(requestBody.payoutBlockNumber),
                ignoredAssetAddresses = requestBody.ignoredHolderAddresses.mapTo(HashSet()) { WalletAddress(it) }
            )
        )

        return ResponseEntity.ok(CreatePayoutResponse(taskId))
    }

    private fun matchCreatedPayoutsWithSuccessfulTasks(
        successfulTasks: List<FullCreatePayoutTask>,
        createdPayouts: List<Payout>,
        issuer: String?
    ): List<PayoutResponse> {
        val tasksByKey = successfulTasks.groupBy { it.toKey() }
        val payoutsByKey = createdPayouts.groupBy { it.toKey() }
        val allKeys = (tasksByKey.keys + payoutsByKey.keys).filterNotNull()

        return allKeys.flatMap { key ->
            val tasks = tasksByKey[key] ?: emptyList()
            val payouts = payoutsByKey[key] ?: emptyList()

            val matchedTasks = tasks.take(payouts.size)
            val matchedPayouts = payouts.take(tasks.size)
            val payoutsWithTask = matchedPayouts.zip(matchedTasks).map {
                it.first.toPayoutResponse(taskId = it.second.taskId, issuer = it.second.issuerAddress?.rawValue)
            }

            val remainingTasks = tasks.drop(payouts.size).map { it.toPayoutResponse() }
            val remainingPayouts = payouts.drop(tasks.size).map { it.toPayoutResponse(taskId = null, issuer = issuer) }

            payoutsWithTask + remainingPayouts + remainingTasks
        }
    }
}
