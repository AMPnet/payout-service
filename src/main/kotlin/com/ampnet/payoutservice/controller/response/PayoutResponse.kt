package com.ampnet.payoutservice.controller.response

import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import java.math.BigInteger

data class PayoutResponse(
    @JsonSerialize(using = ToStringSerializer::class)
    val payoutId: BigInteger,
    val payoutOwner: String,
    val payoutInfo: String,
    val isCanceled: Boolean,

    val asset: String,
    @JsonSerialize(using = ToStringSerializer::class)
    val totalAssetAmount: BigInteger,
    val ignoredHolderAddresses: Set<String>,

    val assetSnapshotMerkleRoot: String,
    val assetSnapshotMerkleDepth: Int,
    @JsonSerialize(using = ToStringSerializer::class)
    val assetSnapshotBlockNumber: BigInteger,
    val assetSnapshotMerkleIpfsHash: String,

    val rewardAsset: String,
    @JsonSerialize(using = ToStringSerializer::class)
    val totalRewardAmount: BigInteger,
    @JsonSerialize(using = ToStringSerializer::class)
    val remainingRewardAmount: BigInteger
)
