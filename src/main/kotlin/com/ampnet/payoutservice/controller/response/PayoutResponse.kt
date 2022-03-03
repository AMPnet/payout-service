package com.ampnet.payoutservice.controller.response

import com.ampnet.payoutservice.util.PayoutStatus
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import java.math.BigInteger
import java.util.UUID

data class PayoutResponse(
    val taskId: UUID?,
    val status: PayoutStatus,
    val issuer: String?,

    @JsonSerialize(using = ToStringSerializer::class)
    val payoutId: BigInteger?,
    val payoutOwner: String,
    val payoutInfo: String?,
    val isCanceled: Boolean?,

    val asset: String,
    @JsonSerialize(using = ToStringSerializer::class)
    val totalAssetAmount: BigInteger?,
    val ignoredAssetAddresses: Set<String>,

    val assetSnapshotMerkleRoot: String?,
    val assetSnapshotMerkleDepth: Int?,
    @JsonSerialize(using = ToStringSerializer::class)
    val assetSnapshotBlockNumber: BigInteger,
    val assetSnapshotMerkleIpfsHash: String?,

    val rewardAsset: String?,
    @JsonSerialize(using = ToStringSerializer::class)
    val totalRewardAmount: BigInteger?,
    @JsonSerialize(using = ToStringSerializer::class)
    val remainingRewardAmount: BigInteger?
)
