package com.ampnet.payoutservice.controller

import com.ampnet.payoutservice.TestBase
import com.ampnet.payoutservice.controller.request.CreatePayoutRequest
import com.ampnet.payoutservice.controller.response.CreatePayoutResponse
import com.ampnet.payoutservice.controller.response.CreatePayoutTaskResponse
import com.ampnet.payoutservice.exception.ResourceNotFoundException
import com.ampnet.payoutservice.model.params.CreatePayoutTaskParams
import com.ampnet.payoutservice.service.CreatePayoutQueueService
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.TaskStatus
import com.ampnet.payoutservice.util.WalletAddress
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.springframework.http.ResponseEntity
import java.math.BigInteger
import java.util.UUID

class PayoutControllerTest : TestBase() {

    @Test
    fun mustCorrectlyFetchPayoutTaskById() {
        val taskUuid = UUID.randomUUID()
        val task = CreatePayoutTaskResponse(
            taskId = taskUuid,
            chainId = 1L,
            assetAddress = "a",
            payoutBlockNumber = BigInteger.ONE,
            ignoredAssetAddresses = setOf("b"),
            requesterAddress = "c",
            issuerAddress = "d",
            taskStatus = TaskStatus.PENDING,
            data = null
        )

        val service = mock<CreatePayoutQueueService>()

        suppose("some task will be returned") {
            given(service.getTaskById(taskUuid))
                .willReturn(task)
        }

        val controller = PayoutController(service)

        verify("correct response is returned") {
            val controllerResponse = controller.getPayoutTaskById(taskUuid)

            assertThat(controllerResponse).withMessage()
                .isEqualTo(ResponseEntity.ok(task))
        }
    }

    @Test
    fun mustThrowResourceNotFoundExceptionForNonExistentPayoutTask() {
        val service = mock<CreatePayoutQueueService>()

        suppose("null will be returned") {
            given(service.getTaskById(any()))
                .willReturn(null)
        }

        val controller = PayoutController(service)

        verify("exception is thrown") {
            assertThrows<ResourceNotFoundException>(message) {
                controller.getPayoutTaskById(UUID.randomUUID())
            }
        }
    }

    @Test
    fun mustCorrectlyCreatePayoutTaskAndReturnAResponse() {
        val service = mock<CreatePayoutQueueService>()
        val chainId = ChainId(1L)
        val assetAddress = ContractAddress("a")
        val requesterAddress = WalletAddress("b")
        val requestBody = CreatePayoutRequest(
            payoutBlockNumber = BigInteger.TEN,
            ignoredAssetAddresses = setOf("f"),
            issuerAddress = "c"
        )
        val taskUuid = UUID.randomUUID()

        suppose("create payout task will be submitted") {
            given(
                service.submitTask(
                    CreatePayoutTaskParams(
                        chainId = chainId,
                        assetAddress = assetAddress,
                        requesterAddress = requesterAddress,
                        issuerAddress = requestBody.issuerAddress?.let { ContractAddress(it) },
                        payoutBlock = BlockNumber(requestBody.payoutBlockNumber),
                        ignoredAssetAddresses = requestBody.ignoredAssetAddresses.mapTo(HashSet()) { WalletAddress(it) }
                    )
                )
            )
                .willReturn(taskUuid)
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
                .isEqualTo(ResponseEntity.ok(CreatePayoutResponse(taskUuid)))
        }
    }
}
