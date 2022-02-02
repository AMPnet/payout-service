package com.ampnet.payoutservice.util

import com.ampnet.payoutservice.util.json.MerkleTreeJsonSerializer
import com.ampnet.payoutservice.util.json.PathSegmentJsonSerializer
import com.ampnet.payoutservice.util.recursion.FlatMap
import com.ampnet.payoutservice.util.recursion.Return
import com.ampnet.payoutservice.util.recursion.Suspend
import com.ampnet.payoutservice.util.recursion.Trampoline
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import java.util.LinkedList
import java.util.SortedMap

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
            override val hash: Hash = Hash("0x0000000000000000000000000000000000000000000000000000000000000000")
        }

        data class LeafNode(val data: AccountBalance, override val hash: Hash) : Node
        data class MiddleNode(override val left: Node, override val right: Node, override val hash: Hash) : PathNode
        data class RootNode(
            override val left: Node,
            override val right: Node,
            override val hash: Hash,
            val depth: Int
        ) : PathNode

        @JsonSerialize(using = PathSegmentJsonSerializer::class)
        data class PathSegment(val siblingHash: Hash, val isLeft: Boolean)
    }

    val leafNodesByHash: Map<Hash, IndexedValue<LeafNode>>
    val leafNodesByAddress: Map<WalletAddress, IndexedValue<LeafNode>>
    val root: RootNode

    init {
        require(nodes.isNotEmpty()) { "Cannot build Merkle tree from empty list" }

        val byAddress: Map<WalletAddress, LeafNode> = nodes.map { LeafNode(it, it.hash) }
            .groupBy { it.data.address }
            .mapValues {
                require(it.value.size == 1) { "Address collision while constructing leaf nodes: ${it.key}" }
                it.value.first()
            }
        val bySortedHash: SortedMap<Hash, LeafNode> = byAddress.values
            .groupBy { it.hash }
            .mapValues {
                require(it.value.size == 1) { "Hash collision while constructing leaf nodes: ${it.key}" }
                it.value.first()
            }.toSortedMap()

        root = buildTree(bySortedHash.values.toList())

        val indexedLeafNodes = indexLeafNodes()

        leafNodesByHash = indexedLeafNodes.associateBy { it.value.hash }
        leafNodesByAddress = indexedLeafNodes.associateBy { it.value.data.address }
    }

    fun pathTo(element: AccountBalance): List<PathSegment>? {
        val index = leafNodesByHash[element.hash]?.index ?: return null
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

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }

        if (other !is MerkleTree) {
            return false
        }

        return other.root == root
    }

    override fun hashCode(): Int {
        return root.hashCode()
    }

    private fun buildTree(leafNodes: List<LeafNode>): RootNode {
        tailrec fun buildLayer(nodes: Collection<Node>, depth: Int): RootNode {
            val pairs = nodes.pairwise()

            return if (pairs.size == 1) {
                val pair = pairs[0]
                RootNode(pair.left, pair.right, pair.hash, depth)
            } else {
                val parentLayer = pairs.map { MiddleNode(it.left, it.right, it.hash) }
                buildLayer(parentLayer, depth + 1)
            }
        }

        return buildLayer(leafNodes, 1)
    }

    private fun indexLeafNodes(): List<IndexedValue<LeafNode>> {

        fun indexPath(currentNode: Node, currentIndex: String): Trampoline<List<IndexedValue<LeafNode>>> {
            return when (currentNode) {
                is PathNode -> {
                    val left = Suspend { indexPath(currentNode.left, currentIndex + "0") }
                    val right = Suspend { indexPath(currentNode.right, currentIndex + "1") }

                    FlatMap(left) { leftList ->
                        FlatMap(right) { rightList ->
                            Return(leftList + rightList)
                        }
                    }
                }

                is LeafNode -> {
                    Return(listOf(IndexedValue(currentIndex.toInt(2), currentNode)))
                }

                else -> {
                    Return(emptyList())
                }
            }
        }

        return Trampoline.run(indexPath(root, "0"))
    }

    private val AccountBalance.hash: Hash
        get() = hashFn(abiEncode())

    private val Pair<Node, Node>.left: Node
        get() = if (first.hash <= second.hash) first else second

    private val Pair<Node, Node>.right: Node
        get() = if (first.hash <= second.hash) second else first

    private val Pair<Node, Node>.hash: Hash
        get() = hashFn((left.hash + right.hash).value)

    private fun Collection<Node>.pairwise(): List<Pair<Node, Node>> =
        this.chunked(2).map { Pair(it.first(), it.getOrNull(1) ?: NilNode) }

    private fun LinkedList<PathSegment>.withFirst(first: PathSegment): LinkedList<PathSegment> {
        addFirst(first)
        return this
    }
}
