package com.ampnet.payoutservice.controller.response

import com.ampnet.payoutservice.util.MerkleTree.Companion.PathSegment

data class FetchMerkleTreePathResponse(
    val walletAddress: String,
    val walletBalance: String,
    val path: List<PathSegment>
)
