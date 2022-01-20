package com.ampnet.payoutservice.util

import java.util.LinkedList

class MerkleTree(nodes: List<AccountBalance>, val hashFn: HashFunction) {

    companion object {
        sealed interface Node {
            val hash: Hash
        }

        sealed interface PathNode : Node {
            val left: Node
            val right: Node
        }

        object NilNode : Node {
            override val hash: Hash = Hash("0")
        }

        data class LeafNode(val data: AccountBalance, override val hash: Hash, val index: Int) : Node
        data class MiddleNode(override val left: Node, override val right: Node, override val hash: Hash) : PathNode
        data class RootNode(
            override val left: Node,
            override val right: Node,
            override val hash: Hash,
            val depth: Int
        ) : PathNode
    }

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

    fun pathTo(element: AccountBalance): List<Hash>? {
        val index = leafNodes[element.hash]?.index ?: return null
        val moves = index.toString(2).padStart(root.depth, '0')

        tailrec fun findPath(currentNode: Node, d: Int, path: LinkedList<Hash>): List<Hash> {
            return if (currentNode is PathNode) {
                val isLeft = moves[d] == '0'
                val nextNode = if (isLeft) currentNode.left else currentNode.right
                val siblingNode = if (isLeft.not()) currentNode.left else currentNode.right
                findPath(nextNode, d + 1, path.withFirst(siblingNode.hash))
            } else {
                path
            }
        }

        return findPath(root, 0, LinkedList())
    }

    private fun buildTree(sortedNodes: Set<AccountBalance>): RootNode {
        val leafNodes = sortedNodes.mapIndexed { index, node -> LeafNode(node, node.hash, index) }

        tailrec fun buildLayer(nodes: Collection<Node>, depth: Int): RootNode {
            val pairs = nodes.pairwise()

            return if (pairs.size == 1) {
                val pair = pairs[0]
                RootNode(pair.first, pair.second, pair.hash, depth)
            } else {
                val parentLayer = pairs.map { MiddleNode(it.first, it.second, it.hash) }
                buildLayer(parentLayer, depth + 1)
            }
        }

        return buildLayer(leafNodes, 1)
    }

    private val AccountBalance.hash: Hash
        get() = hashFn(abiEncode())

    private val Pair<Node, Node>.hash: Hash
        get() = hashFn((first.hash + second.hash).value)

    private fun Collection<Node>.pairwise(): List<Pair<Node, Node>> =
        this.chunked(2).map { Pair(it.first(), it.getOrNull(1) ?: NilNode) }

    private fun LinkedList<Hash>.withFirst(first: Hash): LinkedList<Hash> {
        addFirst(first)
        return this
    }
}
