package com.ampnet.payoutservice.controller.response

import com.ampnet.payoutservice.util.MerkleTree.Companion.PathSegment
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import java.math.BigInteger

data class FetchMerkleTreePathResponse(
    val walletAddress: String,
    @JsonSerialize(using = ToStringSerializer::class)
    val walletBalance: BigInteger,
    val path: List<PathSegment>
) {
    @Suppress("unused") // returned in JSON
    @JsonProperty
    private val proof = path.map { it.siblingHash.value }
}
