package com.ampnet.payoutservice.controller.response

import com.ampnet.payoutservice.util.HashFunction
import com.ampnet.payoutservice.util.TaskStatus
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import java.math.BigInteger
import java.util.UUID

data class CreatePayoutTaskResponse(
    val taskId: UUID,
    val chainId: Long,
    val assetAddress: String,
    @JsonSerialize(using = ToStringSerializer::class)
    val payoutBlockNumber: BigInteger,
    val ignoredAssetAddresses: Set<String>,
    val requesterAddress: String,
    val issuerAddress: String?,
    val taskStatus: TaskStatus,
    val data: CreatePayoutData?
)

data class CreatePayoutData(
    @JsonSerialize(using = ToStringSerializer::class)
    val totalAssetAmount: BigInteger,
    val merkleRootHash: String,
    val merkleTreeIpfsHash: String,
    val merkleTreeDepth: Int,
    val hashFn: HashFunction
)
