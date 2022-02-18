package com.ampnet.payoutservice.controller

import com.ampnet.payoutservice.controller.request.CreatePayoutRequest
import com.ampnet.payoutservice.controller.response.CreatePayoutResponse
import com.ampnet.payoutservice.controller.response.CreatePayoutTaskResponse
import com.ampnet.payoutservice.exception.ErrorCode
import com.ampnet.payoutservice.exception.ResourceNotFoundException
import com.ampnet.payoutservice.model.params.CreatePayoutTaskParams
import com.ampnet.payoutservice.service.CreatePayoutQueueService
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.WalletAddress
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
class PayoutController(val createPayoutQueueService: CreatePayoutQueueService) { // TODO test

    @GetMapping("/payouts/tasks/{taskId}")
    fun getPayoutTaskById(@PathVariable taskId: UUID): ResponseEntity<CreatePayoutTaskResponse> {
        return createPayoutQueueService.getTaskById(taskId)
            ?.let { ResponseEntity.ok(it) }
            ?: throw ResourceNotFoundException(
                ErrorCode.PAYOUT_TASK_NOT_FOUND,
                "Create payout task not found"
            )
    }

    @PostMapping("/payouts/{chainId}/{assetAddress}/create")
    fun createPayout(
        @PathVariable chainId: Long,
        @PathVariable assetAddress: String,
        @RequestBody requestBody: CreatePayoutRequest,
        @AuthenticationPrincipal requesterAddress: String
    ): ResponseEntity<CreatePayoutResponse> {
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
}
