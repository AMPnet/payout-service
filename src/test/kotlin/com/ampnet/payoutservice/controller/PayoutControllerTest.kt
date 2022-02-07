package com.ampnet.payoutservice.controller

import com.ampnet.payoutservice.TestBase
import com.ampnet.payoutservice.controller.request.CreatePayoutRequest
import com.ampnet.payoutservice.controller.response.CreatePayoutResponse
import com.ampnet.payoutservice.service.PayoutService
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.Hash
import com.ampnet.payoutservice.util.WalletAddress
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.springframework.http.ResponseEntity
import java.math.BigInteger

class PayoutControllerTest : TestBase() {

    @Test
    fun mustCorrectlyCreatePayoutAndReturnAResponse() {
        val service = mock<PayoutService>()
        val chainId = ChainId(1L)
        val assetAddress = ContractAddress("a")
        val requesterAddress = WalletAddress("b")
        val requestBody = CreatePayoutRequest(
            payoutBlockNumber = BigInteger.TEN,
            ignoredAssetAddresses = setOf("f")
        )
        val totalAssetAmount = BigInteger.TEN
        val rootHash = Hash("test")
        val ipfsHash = "testIpfsHash"
        val treeDepth = 3

        val serviceResponse = CreatePayoutResponse(
            totalAssetAmount,
            requestBody.ignoredAssetAddresses.mapTo(HashSet()) { WalletAddress(it).rawValue },
            requestBody.payoutBlockNumber,
            rootHash.value,
            ipfsHash,
            treeDepth
        )

        suppose("some Merkle tree hash is returned") {
            given(
                service.createPayout(
                    chainId = chainId,
                    assetAddress = assetAddress,
                    requesterAddress = requesterAddress,
                    payoutBlock = BlockNumber(requestBody.payoutBlockNumber),
                    ignoredAssetAddresses = requestBody.ignoredAssetAddresses.mapTo(HashSet()) { WalletAddress(it) }
                )
            )
                .willReturn(serviceResponse)
        }

        val controller = PayoutController(service)

        verify("correct response is returned") {
            val controllerResponse = controller.createPayout(
                chainId = chainId.value,
                assetAddress = assetAddress.rawValue,
                requestBody = requestBody,
                requesterAddress = requesterAddress.rawValue
            )

            assertThat(controllerResponse).withMessage()
                .isEqualTo(ResponseEntity.ok(serviceResponse))
        }
    }
}
