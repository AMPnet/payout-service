package com.ampnet.payoutservice.util

class MerkleTree(nodes: List<AccountBalance>, private val hashFn: (String) -> Hash) {

    val leafNodes: Map<Hash, LeafNode>
    val root: RootNode

    init {
        require(nodes.isNotEmpty()) { "Cannot build Merkle tree from empty list" }

        val sortedNodes = nodes.toSortedSet()

        leafNodes = sortedNodes.mapIndexed { index, node -> LeafNode(node, node.hash, index) }
            .groupBy { it.hash }
            .mapValues {
                require(it.value.size == 1) { "Hash collision while constructing leaf nodes: ${it.key}" }
                it.value.first()
            }
        root = buildTree(sortedNodes)
    }

    companion object {
        sealed interface Node {
            val hash: Hash
        }

        object NilNode : Node {
            override val hash: Hash = Hash("")
        }

        data class LeafNode(val data: AccountBalance, override val hash: Hash, val index: Int) : Node
        data class MiddleNode(val left: Node, val right: Node, override val hash: Hash) : Node
        data class RootNode(val left: Node, val right: Node, override val hash: Hash) : Node
    }

    private fun buildTree(sortedNodes: Set<AccountBalance>): RootNode {
        val leafNodes = sortedNodes.mapIndexed { index, node -> LeafNode(node, node.hash, index) }

        tailrec fun buildLayer(nodes: Collection<Node>): RootNode {
            val pairs = nodes.pairwise()

            return if (pairs.size == 1) {
                val pair = pairs[0]
                RootNode(pair.first, pair.second, pair.hash)
            } else {
                val parentLayer = pairs.map { MiddleNode(it.first, it.second, it.hash) }
                buildLayer(parentLayer)
            }
        }

        return buildLayer(leafNodes)
    }

    // TODO implement path finding

    private val AccountBalance.hash: Hash
        get() = hashFn(abiEncode())

    private val Pair<Node, Node>.hash: Hash
        get() = hashFn(first.hash.value + second.hash.value)

    private fun Collection<Node>.pairwise(): List<Pair<Node, Node>> =
        this.chunked(2).map { Pair(it.first(), it.getOrNull(1) ?: NilNode) }
}
