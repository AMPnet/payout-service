package com.ampnet.payoutservice.util.json

import com.ampnet.payoutservice.TestBase
import com.ampnet.payoutservice.util.Hash
import com.ampnet.payoutservice.util.MerkleTree.Companion.PathSegment
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PathSegmentJsonSerializerTest : TestBase() {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun mustCorrectlySerializePathSegment() {
        val pathSegment = PathSegment(Hash("test"), true)

        val serializedPathSegment = suppose("path segment is serialized to JSON") {
            objectMapper.valueToTree<JsonNode>(pathSegment)
        }

        verify("path segment is correctly serialized") {
            assertThat(serializedPathSegment).withMessage().isEqualTo(
                objectMapper.readTree(
                    """
                    {
                        "sibling_hash": "${pathSegment.siblingHash.value}",
                        "is_left": ${pathSegment.isLeft}
                    }
                    """.trimIndent()
                )
            )
        }
    }
}
