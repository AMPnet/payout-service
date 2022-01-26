package com.ampnet.payoutservice.controller

import com.ampnet.payoutservice.controller.request.CreatePayoutRequest
import com.ampnet.payoutservice.controller.response.CreatePayoutResponse
import com.ampnet.payoutservice.service.PayoutService
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.WalletAddress
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController

@RestController
class PayoutController(private val payoutService: PayoutService) {

    @PostMapping("/payout/{chainId}/{assetAddress}/create")
    fun createPayout(
        @PathVariable chainId: Long,
        @PathVariable assetAddress: String,
        @RequestBody requestBody: CreatePayoutRequest,
        @AuthenticationPrincipal requesterAddress: String
    ): ResponseEntity<CreatePayoutResponse> {
        val response = payoutService.createPayout(
            chainId = ChainId(chainId),
            assetAddress = ContractAddress(assetAddress),
            requesterAddress = WalletAddress(requesterAddress),
            payoutBlock = BlockNumber(requestBody.payoutBlockNumber)
        )

        return ResponseEntity.ok(response)
    }
}
