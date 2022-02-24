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

    @GetMapping("/payouts/{chainId}/task/{taskId}")  // TODO update docs
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

    @GetMapping("/payouts/{chainId}") // TODO document
    fun getPayouts(
        @PathVariable chainId: Long,
        @RequestParam(required = false) issuer: String?,
        @RequestParam(required = false) owner: String?,
        @RequestParam(required = false) status: List<PayoutStatus>?,
        @RequestParam(required = true) assetFactories: List<String>,
        @RequestParam(required = true) payoutService: String,
        @RequestParam(required = true) payoutManager: String
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
        val allPayouts = otherPayouts + matchCreatedPayoutsWithSuccessfulTasks(successfulTasks, createdPayouts, issuer)

        return ResponseEntity.ok(AdminPayoutsResponse(allPayouts))
    }

    @GetMapping("/payouts/{chainId}/investor/{investorAddress}")  // TODO document
    fun getPayoutsForInvestor(
        @PathVariable chainId: Long,
        @PathVariable investorAddress: String,
        @RequestParam(required = false) issuer: String?,
        @RequestParam(required = true) assetFactories: List<String>,
        @RequestParam(required = true) payoutService: String,
        @RequestParam(required = true) payoutManager: String
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
                val payout = payoutData.payout.toPayoutResponse(taskId = null, issuer = issuer)
                val amountClaimable = accountBalance.balance.rawValue - payoutData.amountClaimed.rawValue

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

    @PostMapping("/payouts/{chainId}/{assetAddress}/create")
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
                ignoredAssetAddresses = requestBody.ignoredAssetAddresses.mapTo(HashSet()) { WalletAddress(it) }
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

            val matchedTasks = tasks.subList(0, payouts.size)
            val matchedPayouts = payouts.subList(0, tasks.size)
            val payoutsWithTask = matchedPayouts.zip(matchedTasks).map {
                it.first.toPayoutResponse(taskId = it.second.taskId, issuer = it.second.issuerAddress?.rawValue)
            }

            val remainingTasks = tasks.drop(payouts.size).map { it.toPayoutResponse() }
            val remainingPayouts = payouts.drop(tasks.size).map { it.toPayoutResponse(taskId = null, issuer = issuer) }

            payoutsWithTask + remainingTasks + remainingPayouts
        }
    }

    private fun FullCreatePayoutTask.toPayoutResponse(): PayoutResponse =
        PayoutResponse(
            taskId = taskId,
            status = taskStatus.toPayoutStatus,
            issuer = issuerAddress?.rawValue,

            payoutId = null,
            payoutOwner = requesterAddress.rawValue,
            payoutInfo = null,
            isCanceled = null,

            asset = assetAddress.rawValue,
            totalAssetAmount = null,
            ignoredAssetAddresses = ignoredAssetAddresses.mapTo(HashSet()) { it.rawValue },

            assetSnapshotMerkleRoot = null,
            assetSnapshotMerkleDepth = null,
            assetSnapshotBlockNumber = payoutBlockNumber.value,

            rewardAsset = null,
            totalRewardAmount = null,
            remainingRewardAmount = null
        )

    private fun Payout.toPayoutResponse(taskId: UUID?, issuer: String?): PayoutResponse =
        PayoutResponse(
            taskId = taskId,
            status = PayoutStatus.PAYOUT_CREATED,
            issuer = issuer,

            payoutId = payoutId,
            payoutOwner = payoutOwner.rawValue,
            payoutInfo = payoutInfo,
            isCanceled = isCanceled,

            asset = asset.rawValue,
            totalAssetAmount = totalAssetAmount.rawValue,
            ignoredAssetAddresses = ignoredAssetAddresses.mapTo(HashSet()) { it.rawValue },

            assetSnapshotMerkleRoot = assetSnapshotMerkleRoot.value,
            assetSnapshotMerkleDepth = assetSnapshotMerkleDepth.intValueExact(),
            assetSnapshotBlockNumber = assetSnapshotBlockNumber.value,

            rewardAsset = rewardAsset.rawValue,
            totalRewardAmount = totalRewardAmount.rawValue,
            remainingRewardAmount = remainingRewardAmount.rawValue
        )
}
