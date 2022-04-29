package com.ampnet.payoutservice.controller

import com.ampnet.payoutservice.TestBase
import com.ampnet.payoutservice.controller.request.CreateSnapshotRequest
import com.ampnet.payoutservice.controller.response.CreateSnapshotResponse
import com.ampnet.payoutservice.controller.response.SnapshotsResponse
import com.ampnet.payoutservice.exception.ResourceNotFoundException
import com.ampnet.payoutservice.model.params.CreateSnapshotParams
import com.ampnet.payoutservice.model.result.FullSnapshot
import com.ampnet.payoutservice.model.result.FullSnapshotData
import com.ampnet.payoutservice.service.SnapshotQueueService
import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.Hash
import com.ampnet.payoutservice.util.HashFunction
import com.ampnet.payoutservice.util.IpfsHash
import com.ampnet.payoutservice.util.SnapshotStatus
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

class SnapshotControllerTest : TestBase() {

    @Test
    fun mustCorrectlyFetchSnapshotById() {
        val snapshotUuid = UUID.randomUUID()
        val snapshot = FullSnapshot(
            id = snapshotUuid,
            name = "snapshot-name",
            chainId = ChainId(1L),
            assetAddress = ContractAddress("a"),
            blockNumber = BlockNumber(BigInteger.ONE),
            ignoredHolderAddresses = setOf(WalletAddress("b")),
            ownerAddress = WalletAddress("c"),
            snapshotStatus = SnapshotStatus.PENDING,
            snapshotFailureCause = null,
            data = null
        )

        val service = mock<SnapshotQueueService>()

        suppose("some snapshot will be returned") {
            given(service.getSnapshotById(snapshotUuid))
                .willReturn(snapshot)
        }

        val controller = SnapshotController(service)

        verify("correct response is returned") {
            val controllerResponse = controller.getSnapshotById(snapshotUuid, snapshot.ownerAddress.rawValue)

            assertThat(controllerResponse).withMessage()
                .isEqualTo(ResponseEntity.ok(snapshot.toSnapshotResponse()))
        }

        verify("exception is thrown for wrong owner address") {
            assertThrows<ResourceNotFoundException>(message) {
                controller.getSnapshotById(snapshotUuid, "dead")
            }
        }
    }

    @Test
    fun mustThrowResourceNotFoundExceptionForNonExistentSnapshot() {
        val service = mock<SnapshotQueueService>()

        suppose("null will be returned") {
            given(service.getSnapshotById(any()))
                .willReturn(null)
        }

        val controller = SnapshotController(service)

        verify("exception is thrown") {
            assertThrows<ResourceNotFoundException>(message) {
                controller.getSnapshotById(UUID.randomUUID(), "a")
            }
        }
    }

    @Test
    fun mustCorrectlyFetchSnapshotsWithNullChainIdAndStatus() {
        val owner = WalletAddress("123")
        val snapshots = listOf(
            createSuccessfulSnapshot(0, ChainId(123L), owner),
            createSuccessfulSnapshot(1, ChainId(456L), owner),
            createPendingSnapshot(2, ChainId(789L), owner)
        )
        val service = mock<SnapshotQueueService>()

        suppose("some snapshots will be returned") {
            given(
                service.getAllSnapshotsByChainIdOwnerAndStatuses(
                    chainId = null,
                    owner = owner,
                    statuses = emptySet()
                )
            )
                .willReturn(snapshots)
        }

        val controller = SnapshotController(service)

        verify("correct snapshots are returned") {
            val result = controller.getSnapshots(
                chainId = null,
                status = null,
                ownerAddress = owner.rawValue
            )

            assertThat(result).withMessage()
                .isEqualTo(ResponseEntity.ok(SnapshotsResponse(snapshots.map { it.toSnapshotResponse() })))
        }
    }

    @Test
    fun mustCorrectlyFetchSnapshotsWithSomeChainId() {
        val chainId = ChainId(123L)
        val owner = WalletAddress("123")
        val snapshots = listOf(
            createSuccessfulSnapshot(0, chainId, owner),
            createSuccessfulSnapshot(1, chainId, owner),
            createPendingSnapshot(2, chainId, owner)
        )
        val service = mock<SnapshotQueueService>()

        suppose("some snapshots will be returned") {
            given(
                service.getAllSnapshotsByChainIdOwnerAndStatuses(
                    chainId = chainId,
                    owner = owner,
                    statuses = emptySet()
                )
            )
                .willReturn(snapshots)
        }

        val controller = SnapshotController(service)

        verify("correct snapshots are returned") {
            val result = controller.getSnapshots(
                chainId = chainId.value,
                status = null,
                ownerAddress = owner.rawValue,
            )

            assertThat(result).withMessage()
                .isEqualTo(ResponseEntity.ok(SnapshotsResponse(snapshots.map { it.toSnapshotResponse() })))
        }
    }

    @Test
    fun mustCorrectlyFetchSnapshotsWithSomeStatusFilter() {
        val owner = WalletAddress("123")
        val snapshots = listOf(
            createSuccessfulSnapshot(0, ChainId(123L), owner),
            createSuccessfulSnapshot(1, ChainId(456L), owner),
            createPendingSnapshot(2, ChainId(789L), owner)
        )
        val service = mock<SnapshotQueueService>()
        val status = listOf(
            SnapshotStatus.PENDING,
            SnapshotStatus.SUCCESS
        )

        suppose("some snapshots will be returned") {
            given(
                service.getAllSnapshotsByChainIdOwnerAndStatuses(
                    chainId = null,
                    owner = owner,
                    statuses = status.toSet()
                )
            )
                .willReturn(snapshots)
        }

        val controller = SnapshotController(service)

        verify("correct snapshots are returned") {
            val result = controller.getSnapshots(
                chainId = null,
                status = status,
                ownerAddress = owner.rawValue
            )

            assertThat(result).withMessage()
                .isEqualTo(ResponseEntity.ok(SnapshotsResponse(snapshots.map { it.toSnapshotResponse() })))
        }
    }

    @Test
    fun mustCorrectlyCreateSnapshotAndReturnAResponse() {
        val service = mock<SnapshotQueueService>()
        val chainId = ChainId(1L)
        val name = "snapshot-name"
        val assetAddress = ContractAddress("a")
        val ownerAddress = WalletAddress("b")
        val requestBody = CreateSnapshotRequest(
            name = name,
            chainId = chainId.value,
            assetAddress = assetAddress.rawValue,
            payoutBlockNumber = BigInteger.TEN,
            ignoredHolderAddresses = setOf("f"),
        )
        val snapshotUuid = UUID.randomUUID()

        suppose("snapshot will be submitted") {
            given(
                service.submitSnapshot(
                    CreateSnapshotParams(
                        chainId = chainId,
                        name = name,
                        assetAddress = assetAddress,
                        ownerAddress = ownerAddress,
                        payoutBlock = BlockNumber(requestBody.payoutBlockNumber),
                        ignoredHolderAddresses = requestBody.ignoredHolderAddresses
                            .mapTo(HashSet()) { WalletAddress(it) }
                    )
                )
            )
                .willReturn(snapshotUuid)
        }

        val controller = SnapshotController(service)

        verify("correct response is returned") {
            val controllerResponse = controller.createSnapshot(
                requestBody = requestBody,
                ownerAddress = ownerAddress.rawValue
            )

            assertThat(controllerResponse).withMessage()
                .isEqualTo(ResponseEntity.ok(CreateSnapshotResponse(snapshotUuid)))
        }
    }

    private fun createPendingSnapshot(id: Int, chainId: ChainId, owner: WalletAddress): FullSnapshot {
        val uuid = UUID.randomUUID()
        return FullSnapshot(
            id = uuid,
            name = "snapshot-$uuid",
            chainId = chainId,
            assetAddress = ContractAddress("aaa$id"),
            blockNumber = BlockNumber(BigInteger.valueOf(id * 100L)),
            ignoredHolderAddresses = emptySet(),
            ownerAddress = owner,
            snapshotStatus = SnapshotStatus.PENDING,
            snapshotFailureCause = null,
            data = null
        )
    }

    private fun createSuccessfulSnapshot(id: Int, chainId: ChainId, owner: WalletAddress): FullSnapshot =
        createPendingSnapshot(id, chainId, owner)
            .copy(
                snapshotStatus = SnapshotStatus.SUCCESS,
                data = FullSnapshotData(
                    totalAssetAmount = Balance(BigInteger.valueOf(id * 200L)),
                    merkleRootHash = Hash("root-hash-$id"),
                    merkleTreeIpfsHash = IpfsHash("ipfs-hash-$id"),
                    merkleTreeDepth = id,
                    hashFn = HashFunction.KECCAK_256
                )
            )
}
