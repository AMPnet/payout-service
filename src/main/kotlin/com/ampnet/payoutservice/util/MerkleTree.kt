package com.ampnet.payoutservice.util

import com.ampnet.payoutservice.util.json.MerkleTreeJsonSerializer
import com.ampnet.payoutservice.util.json.PathSegmentJsonSerializer
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import java.util.LinkedList

@JsonSerialize(using = MerkleTreeJsonSerializer::class)
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

        @JsonSerialize(using = PathSegmentJsonSerializer::class)
        data class PathSegment(val hash: Hash, val isLeft: Boolean)
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

    fun pathTo(element: AccountBalance): List<PathSegment>? {
        val index = leafNodes[element.hash]?.index ?: return null
        val moves = index.toString(2).padStart(root.depth, '0')

        tailrec fun findPath(currentNode: Node, d: Int, path: LinkedList<PathSegment>): List<PathSegment> {
            return if (currentNode is PathNode) {
                val isLeft = moves[d] == '0'
                val nextNode = if (isLeft) currentNode.left else currentNode.right
                val siblingNode = if (isLeft.not()) currentNode.left else currentNode.right
                findPath(nextNode, d + 1, path.withFirst(PathSegment(siblingNode.hash, isLeft.not())))
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

    private fun LinkedList<PathSegment>.withFirst(first: PathSegment): LinkedList<PathSegment> {
        addFirst(first)
        return this
    }
}
