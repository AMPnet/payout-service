package com.ampnet.payoutservice.util.json

import com.ampnet.payoutservice.util.MerkleTree.Companion.PathSegment
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider

class PathSegmentJsonSerializer : JsonSerializer<PathSegment>() {

    override fun serialize(value: PathSegment, json: JsonGenerator, provider: SerializerProvider) {
        json.apply {
            writeStartObject()

            writeStringField("sibling_hash", value.siblingHash.value)
            writeBooleanField("is_left", value.isLeft)

            writeEndObject()
        }
    }
}
