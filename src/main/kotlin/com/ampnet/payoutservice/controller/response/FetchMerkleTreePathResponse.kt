package com.ampnet.payoutservice.controller.response

import com.ampnet.payoutservice.util.MerkleTree.Companion.PathSegment

data class PathSegmentResponse(val hash: String, val isLeft: Boolean)

data class FetchMerkleTreePathResponse(val path: List<PathSegmentResponse>) {
    companion object {
        fun fromPathSegments(segments: List<PathSegment>) = FetchMerkleTreePathResponse(
            segments.map {
                PathSegmentResponse(
                    it.hash.value,
                    it.isLeft
                )
            }
        )
    }
}
