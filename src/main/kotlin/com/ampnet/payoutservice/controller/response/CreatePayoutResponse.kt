package com.ampnet.payoutservice.controller.response

import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import java.math.BigInteger

data class CreatePayoutResponse(
    @JsonSerialize(using = ToStringSerializer::class)
    val totalAssetAmount: BigInteger,
    val ignoredAssetAddresses: Set<String>,
    @JsonSerialize(using = ToStringSerializer::class)
    val payoutBlockNumber: BigInteger,
    val merkleRootHash: String,
    val merkleTreeIpfsHash: String,
    val merkleTreeDepth: Int
)
