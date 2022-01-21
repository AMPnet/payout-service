package com.ampnet.payoutservice.util.json

import com.ampnet.payoutservice.util.MerkleTree
import com.ampnet.payoutservice.util.MerkleTree.Companion.LeafNode
import com.ampnet.payoutservice.util.MerkleTree.Companion.NilNode
import com.ampnet.payoutservice.util.MerkleTree.Companion.Node
import com.ampnet.payoutservice.util.MerkleTree.Companion.PathNode
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider

class MerkleTreeJsonSerializer : JsonSerializer<MerkleTree>() {

    override fun serialize(value: MerkleTree, json: JsonGenerator, provider: SerializerProvider) {
        json.apply {
            writeStartObject()

            writeNumberField("depth", value.root.depth)
            writeStringField("hash", value.root.hash.value)
            writeStringField("hashFn", value.hashFn.name)

            writeBranch("left", value.root.left)
            writeBranch("right", value.root.right)

            writeEndObject()
        }
    }

    private fun JsonGenerator.writeBranch(fieldName: String, branch: Node) {
        writeObjectFieldStart(fieldName)
        writeStringField("hash", branch.hash.value)

        when (branch) {
            is NilNode -> {}
            is LeafNode -> {
                writeNumberField("index", branch.index)
                writeObjectFieldStart("data")
                writeStringField("address", branch.data.address.rawValue)
                writeStringField("balance", branch.data.balance.rawValue.toString())
                writeEndObject()
            }
            is PathNode -> {
                writeBranch("left", branch.left)
                writeBranch("right", branch.right)
            }
        }

        writeEndObject()
    }
}
