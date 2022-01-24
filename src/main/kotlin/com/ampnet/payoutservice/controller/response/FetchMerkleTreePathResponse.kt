package com.ampnet.payoutservice.controller.response

import com.ampnet.payoutservice.util.MerkleTree.Companion.PathSegment

data class FetchMerkleTreePathResponse(val path: List<PathSegment>)
