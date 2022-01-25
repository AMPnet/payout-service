package com.ampnet.payoutservice.controller.response

import com.ampnet.payoutservice.util.MerkleTree.Companion.PathSegment
import java.math.BigInteger

data class FetchMerkleTreePathResponse(
    val walletAddress: String,
    val walletBalance: BigInteger,
    val path: List<PathSegment>
)
