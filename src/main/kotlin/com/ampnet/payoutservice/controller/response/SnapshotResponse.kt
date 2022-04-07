package com.ampnet.payoutservice.controller.response

import com.ampnet.payoutservice.util.SnapshotStatus
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import java.math.BigInteger
import java.util.UUID

data class SnapshotResponse(
    val id: UUID,
    val name: String,
    val chainId: Long,
    val status: SnapshotStatus,
    val owner: String,
    val asset: String,
    @JsonSerialize(using = ToStringSerializer::class)
    val totalAssetAmount: BigInteger?,
    val ignoredHolderAddresses: Set<String>,
    val assetSnapshotMerkleRoot: String?,
    val assetSnapshotMerkleDepth: Int?,
    @JsonSerialize(using = ToStringSerializer::class)
    val assetSnapshotBlockNumber: BigInteger,
    val assetSnapshotMerkleIpfsHash: String?
)
