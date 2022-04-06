package com.ampnet.payoutservice.controller

import com.ampnet.payoutservice.controller.request.CreateSnapshotRequest
import com.ampnet.payoutservice.controller.response.CreateSnapshotResponse
import com.ampnet.payoutservice.controller.response.SnapshotResponse
import com.ampnet.payoutservice.controller.response.SnapshotsResponse
import com.ampnet.payoutservice.exception.ErrorCode
import com.ampnet.payoutservice.exception.ResourceNotFoundException
import com.ampnet.payoutservice.model.params.CreateSnapshotParams
import com.ampnet.payoutservice.service.SnapshotQueueService
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.SnapshotStatus
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
import java.util.UUID

@RestController
class SnapshotController(private val snapshotQueueService: SnapshotQueueService) {

    companion object : KLogging()

    @GetMapping("/snapshots/{snapshotId}")
    fun getSnapshotById(
        @PathVariable snapshotId: UUID,
        @AuthenticationPrincipal ownerAddress: String
    ): ResponseEntity<SnapshotResponse> {
        logger.debug { "Get snapshot by snapshotId: $snapshotId, ownerAddress: $ownerAddress" }
        return snapshotQueueService.getSnapshotById(snapshotId)
            ?.takeIf { it.ownerAddress == WalletAddress(ownerAddress) }
            ?.let { ResponseEntity.ok(it.toSnapshotResponse()) }
            ?: throw ResourceNotFoundException(
                ErrorCode.SNAPSHOT_NOT_FOUND,
                "Snapshot not found"
            )
    }

    @Suppress("LongParameterList")
    @GetMapping("/snapshots")
    fun getSnapshots(
        @RequestParam(required = false) chainId: Long?,
        @RequestParam(required = false) status: List<SnapshotStatus>?,
        @AuthenticationPrincipal ownerAddress: String
    ): ResponseEntity<SnapshotsResponse> {
        logger.debug { "Get snapshots for owner, chainId: $chainId, ownerAddress: $ownerAddress, statuses: $status" }
        val chainIdValue = chainId?.let { ChainId(it) }
        val ownerAddressValue = WalletAddress(ownerAddress)
        val statuses = status ?: emptyList()

        val snapshots = snapshotQueueService.getAllSnapshotsByChainIdOwnerAndStatuses(
            chainId = chainIdValue,
            owner = ownerAddressValue,
            statuses = statuses.toSet()
        )

        return ResponseEntity.ok(SnapshotsResponse(snapshots.map { it.toSnapshotResponse() }))
    }

    @PostMapping("/snapshots")
    fun createSnapshot(
        @RequestBody requestBody: CreateSnapshotRequest,
        @AuthenticationPrincipal ownerAddress: String
    ): ResponseEntity<CreateSnapshotResponse> {
        logger.debug { "Request snapshot creation, requestBody: $requestBody, ownerAddress: $ownerAddress" }
        val snapshotId = snapshotQueueService.submitSnapshot(
            CreateSnapshotParams(
                chainId = ChainId(requestBody.chainId),
                name = requestBody.name,
                assetAddress = ContractAddress(requestBody.assetAddress),
                ownerAddress = WalletAddress(ownerAddress),
                payoutBlock = BlockNumber(requestBody.payoutBlockNumber),
                ignoredHolderAddresses = requestBody.ignoredHolderAddresses.mapTo(HashSet()) { WalletAddress(it) }
            )
        )

        return ResponseEntity.ok(CreateSnapshotResponse(snapshotId))
    }
}
