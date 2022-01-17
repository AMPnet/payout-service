package com.ampnet.payoutservice.util

import com.ampnet.payoutservice.TestBase
import com.ampnet.payoutservice.util.MerkleTree.Companion.LeafNode
import com.ampnet.payoutservice.util.MerkleTree.Companion.MiddleNode
import com.ampnet.payoutservice.util.MerkleTree.Companion.NilNode
import com.ampnet.payoutservice.util.MerkleTree.Companion.RootNode
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigInteger

class MerkleTreeTest : TestBase() {

    private val identityHashFn: (String) -> Hash = { Hash(it) }
    private val fixedHashFn: (String) -> Hash = { Hash("0") }

    @Test
    fun mustThrowExceptionForEmptyNodeList() {
        verify("exception is thrown when building Merkle tree from empty list") {
            assertThrows<IllegalArgumentException>(message) { MerkleTree(emptyList(), identityHashFn) }
        }
    }

    @Test
    fun mustThrowExceptionForLeafNodeHashCollision() {
        verify("exception is thrown when hash collision in leaf nodes occurs") {
            assertThrows<IllegalArgumentException>(message) {
                MerkleTree(
                    listOf(
                        AccountBalance(WalletAddress("0x0"), Balance(BigInteger("0"))),
                        AccountBalance(WalletAddress("0x1"), Balance(BigInteger("0")))
                    ),
                    fixedHashFn
                )
            }
        }
    }

    @Test
    fun mustCorrectlyBuildMerkleTreeForSingleElement() {
        val balance = AccountBalance(WalletAddress("0x0"), Balance(BigInteger("0")))
        val tree = suppose("Merkle tree with single element is created") {
            MerkleTree(
                listOf(balance),
                identityHashFn
            )
        }

        verify("Merkle tree has correct structure") {
            assertThat(tree.root).withMessage().isEqualTo(
                RootNode(
                    left = LeafNode(balance, Hash(balance.abiEncode()), 0),
                    right = NilNode,
                    hash = Hash(balance.abiEncode())
                )
            )
        }

        verify("Merkle tree has correct leaf nodes") {
            assertThat(tree.leafNodes).withMessage().hasSize(1)
            assertThat(tree.leafNodes).withMessage().containsEntry(
                Hash(balance.abiEncode()), LeafNode(balance, Hash(balance.abiEncode()), 0)
            )
        }
        // TODO test path
    }

    @Test
    fun mustCorrectlyBuildMerkleTreeForTwoElements() {
        val balances = listOf(
            AccountBalance(WalletAddress("0x0"), Balance(BigInteger("0"))),
            AccountBalance(WalletAddress("0x1"), Balance(BigInteger("1")))
        )
        val hashes = balances.hashes()
        val tree = suppose("Merkle tree with two elements is created") {
            MerkleTree(
                balances.shuffled(),
                identityHashFn
            )
        }

        verify("Merkle tree has correct structure") {
            assertThat(tree.root).withMessage().isEqualTo(
                RootNode(
                    left = balances.leafNode(0),
                    right = balances.leafNode(1),
                    hash = hashes.all()
                )
            )
        }

        verify("Merkle tree has correct leaf nodes") {
            assertThat(tree.leafNodes).withMessage().hasSize(2)
            assertThat(tree.leafNodes.toList()).withMessage().containsExactlyInAnyOrderElementsOf(
                balances.mapIndexed { index, node ->
                    Pair(
                        Hash(node.abiEncode()),
                        LeafNode(node, Hash(node.abiEncode()), index)
                    )
                }
            )
        }
        // TODO test path
    }

    @Test
    fun mustCorrectlyBuildMerkleTreeForThreeElements() {
        val balances = listOf(
            AccountBalance(WalletAddress("0x0"), Balance(BigInteger("0"))),
            AccountBalance(WalletAddress("0x1"), Balance(BigInteger("1"))),
            AccountBalance(WalletAddress("0x2"), Balance(BigInteger("2")))
        )
        val hashes = balances.hashes()
        val tree = suppose("Merkle tree with three elements is created") {
            MerkleTree(
                balances.shuffled(),
                identityHashFn
            )
        }

        verify("Merkle tree has correct structure") {
            assertThat(tree.root).withMessage().isEqualTo(
                RootNode(
                    left = MiddleNode(
                        left = balances.leafNode(0),
                        right = balances.leafNode(1),
                        hash = hashes[0..1]
                    ),
                    right = MiddleNode(
                        left = balances.leafNode(2),
                        right = NilNode,
                        hash = hashes[2]
                    ),
                    hash = hashes.all()
                )
            )
        }

        verify("Merkle tree has correct leaf nodes") {
            assertThat(tree.leafNodes).withMessage().hasSize(3)
            assertThat(tree.leafNodes.toList()).withMessage().containsExactlyInAnyOrderElementsOf(
                balances.mapIndexed { index, node ->
                    Pair(
                        Hash(node.abiEncode()),
                        LeafNode(node, Hash(node.abiEncode()), index)
                    )
                }
            )
        }
        // TODO test path
    }

    @Test
    fun mustCorrectlyBuildMerkleTreeForFourElements() {
        val balances = listOf(
            AccountBalance(WalletAddress("0x0"), Balance(BigInteger("0"))),
            AccountBalance(WalletAddress("0x1"), Balance(BigInteger("1"))),
            AccountBalance(WalletAddress("0x2"), Balance(BigInteger("2"))),
            AccountBalance(WalletAddress("0x3"), Balance(BigInteger("3")))
        )
        val hashes = balances.hashes()
        val tree = suppose("Merkle tree with four elements is created") {
            MerkleTree(
                balances.shuffled(),
                identityHashFn
            )
        }

        verify("Merkle tree has correct structure") {
            assertThat(tree.root).withMessage().isEqualTo(
                RootNode(
                    left = MiddleNode(
                        left = balances.leafNode(0),
                        right = balances.leafNode(1),
                        hash = hashes[0..1]
                    ),
                    right = MiddleNode(
                        left = balances.leafNode(2),
                        right = balances.leafNode(3),
                        hash = hashes[2..3]
                    ),
                    hash = hashes.all()
                )
            )
        }

        verify("Merkle tree has correct leaf nodes") {
            assertThat(tree.leafNodes).withMessage().hasSize(4)
            assertThat(tree.leafNodes.toList()).withMessage().containsExactlyInAnyOrderElementsOf(
                balances.mapIndexed { index, node ->
                    Pair(
                        Hash(node.abiEncode()),
                        LeafNode(node, Hash(node.abiEncode()), index)
                    )
                }
            )
        }
        // TODO test path
    }

    @Test
    fun mustCorrectlyBuildBalancedMerkleTree() {
        val balances = listOf(
            AccountBalance(WalletAddress("0x0"), Balance(BigInteger("0"))),
            AccountBalance(WalletAddress("0x1"), Balance(BigInteger("1"))),
            AccountBalance(WalletAddress("0x2"), Balance(BigInteger("2"))),
            AccountBalance(WalletAddress("0x3"), Balance(BigInteger("3"))),
            AccountBalance(WalletAddress("0x4"), Balance(BigInteger("4"))),
            AccountBalance(WalletAddress("0x5"), Balance(BigInteger("5"))),
            AccountBalance(WalletAddress("0x6"), Balance(BigInteger("6"))),
            AccountBalance(WalletAddress("0x7"), Balance(BigInteger("7")))
        )
        val hashes = balances.hashes()
        val tree = suppose("Merkle tree with 8 elements is created") {
            MerkleTree(
                balances.shuffled(),
                identityHashFn
            )
        }

        verify("Merkle tree has correct structure") {
            assertThat(tree.root).withMessage().isEqualTo(
                RootNode(
                    left = MiddleNode(
                        left = MiddleNode(
                            left = balances.leafNode(0),
                            right = balances.leafNode(1),
                            hash = hashes[0..1]
                        ),
                        right = MiddleNode(
                            left = balances.leafNode(2),
                            right = balances.leafNode(3),
                            hash = hashes[2..3]
                        ),
                        hash = hashes[0..3]
                    ),
                    right = MiddleNode(
                        left = MiddleNode(
                            left = balances.leafNode(4),
                            right = balances.leafNode(5),
                            hash = hashes[4..5]
                        ),
                        right = MiddleNode(
                            left = balances.leafNode(6),
                            right = balances.leafNode(7),
                            hash = hashes[6..7]
                        ),
                        hash = hashes[4..7]
                    ),
                    hash = hashes.all()
                )
            )
        }

        verify("Merkle tree has correct leaf nodes") {
            assertThat(tree.leafNodes).withMessage().hasSize(8)
            assertThat(tree.leafNodes.toList()).withMessage().containsExactlyInAnyOrderElementsOf(
                balances.mapIndexed { index, node ->
                    Pair(
                        Hash(node.abiEncode()),
                        LeafNode(node, Hash(node.abiEncode()), index)
                    )
                }
            )
        }
        // TODO test path
    }

    @Test
    fun mustCorrectlyBuildUnbalancedMerkleTreeForEvenNumberOfElements() {
        val balances = listOf(
            AccountBalance(WalletAddress("0x0"), Balance(BigInteger("0"))),
            AccountBalance(WalletAddress("0x1"), Balance(BigInteger("1"))),
            AccountBalance(WalletAddress("0x2"), Balance(BigInteger("2"))),
            AccountBalance(WalletAddress("0x3"), Balance(BigInteger("3"))),
            AccountBalance(WalletAddress("0x4"), Balance(BigInteger("4"))),
            AccountBalance(WalletAddress("0x5"), Balance(BigInteger("5"))),
            AccountBalance(WalletAddress("0x6"), Balance(BigInteger("6"))),
            AccountBalance(WalletAddress("0x7"), Balance(BigInteger("7"))),
            AccountBalance(WalletAddress("0x8"), Balance(BigInteger("8"))),
            AccountBalance(WalletAddress("0x9"), Balance(BigInteger("9"))),
            AccountBalance(WalletAddress("0xa"), Balance(BigInteger("10"))),
            AccountBalance(WalletAddress("0xb"), Balance(BigInteger("11")))
        )
        val hashes = balances.hashes()
        val tree = suppose("Merkle tree with 12 elements is created") {
            MerkleTree(
                balances.shuffled(),
                identityHashFn
            )
        }

        verify("Merkle tree has correct structure") {
            assertThat(tree.root).withMessage().isEqualTo(
                RootNode(
                    left = MiddleNode(
                        left = MiddleNode(
                            left = MiddleNode(
                                left = balances.leafNode(0),
                                right = balances.leafNode(1),
                                hash = hashes[0..1]
                            ),
                            right = MiddleNode(
                                left = balances.leafNode(2),
                                right = balances.leafNode(3),
                                hash = hashes[2..3]
                            ),
                            hash = hashes[0..3]
                        ),
                        right = MiddleNode(
                            left = MiddleNode(
                                left = balances.leafNode(4),
                                right = balances.leafNode(5),
                                hash = hashes[4..5]
                            ),
                            right = MiddleNode(
                                left = balances.leafNode(6),
                                right = balances.leafNode(7),
                                hash = hashes[6..7]
                            ),
                            hash = hashes[4..7]
                        ),
                        hash = hashes[0..7]
                    ),
                    right = MiddleNode(
                        left = MiddleNode(
                            left = MiddleNode(
                                left = balances.leafNode(8),
                                right = balances.leafNode(9),
                                hash = hashes[8..9]
                            ),
                            right = MiddleNode(
                                left = balances.leafNode(10),
                                right = balances.leafNode(11),
                                hash = hashes[10..11]
                            ),
                            hash = hashes[8..11]
                        ),
                        right = NilNode,
                        hash = hashes[8..11]
                    ),
                    hash = hashes.all()
                )
            )
        }

        verify("Merkle tree has correct leaf nodes") {
            assertThat(tree.leafNodes).withMessage().hasSize(12)
            assertThat(tree.leafNodes.toList()).withMessage().containsExactlyInAnyOrderElementsOf(
                balances.mapIndexed { index, node ->
                    Pair(
                        Hash(node.abiEncode()),
                        LeafNode(node, Hash(node.abiEncode()), index)
                    )
                }
            )
        }
        // TODO test path
    }

    @Test
    fun mustCorrectlyBuildUnbalancedMerkleTreeForOddNumberOfElements() {
        val balances = listOf(
            AccountBalance(WalletAddress("0x0"), Balance(BigInteger("0"))),
            AccountBalance(WalletAddress("0x1"), Balance(BigInteger("1"))),
            AccountBalance(WalletAddress("0x2"), Balance(BigInteger("2"))),
            AccountBalance(WalletAddress("0x3"), Balance(BigInteger("3"))),
            AccountBalance(WalletAddress("0x4"), Balance(BigInteger("4"))),
            AccountBalance(WalletAddress("0x5"), Balance(BigInteger("5"))),
            AccountBalance(WalletAddress("0x6"), Balance(BigInteger("6"))),
            AccountBalance(WalletAddress("0x7"), Balance(BigInteger("7"))),
            AccountBalance(WalletAddress("0x8"), Balance(BigInteger("8"))),
            AccountBalance(WalletAddress("0x9"), Balance(BigInteger("9"))),
            AccountBalance(WalletAddress("0xa"), Balance(BigInteger("10"))),
            AccountBalance(WalletAddress("0xb"), Balance(BigInteger("11"))),
            AccountBalance(WalletAddress("0xc"), Balance(BigInteger("12")))
        )
        val hashes = balances.hashes()
        val tree = suppose("Merkle tree with 13 elements is created") {
            MerkleTree(
                balances.shuffled(),
                identityHashFn
            )
        }

        verify("Merkle tree has correct structure") {
            assertThat(tree.root).withMessage().isEqualTo(
                RootNode(
                    left = MiddleNode(
                        left = MiddleNode(
                            left = MiddleNode(
                                left = balances.leafNode(0),
                                right = balances.leafNode(1),
                                hash = hashes[0..1]
                            ),
                            right = MiddleNode(
                                left = balances.leafNode(2),
                                right = balances.leafNode(3),
                                hash = hashes[2..3]
                            ),
                            hash = hashes[0..3]
                        ),
                        right = MiddleNode(
                            left = MiddleNode(
                                left = balances.leafNode(4),
                                right = balances.leafNode(5),
                                hash = hashes[4..5]
                            ),
                            right = MiddleNode(
                                left = balances.leafNode(6),
                                right = balances.leafNode(7),
                                hash = hashes[6..7]
                            ),
                            hash = hashes[4..7]
                        ),
                        hash = hashes[0..7]
                    ),
                    right = MiddleNode(
                        left = MiddleNode(
                            left = MiddleNode(
                                left = balances.leafNode(8),
                                right = balances.leafNode(9),
                                hash = hashes[8..9]
                            ),
                            right = MiddleNode(
                                left = balances.leafNode(10),
                                right = balances.leafNode(11),
                                hash = hashes[10..11]
                            ),
                            hash = hashes[8..11]
                        ),
                        right = MiddleNode(
                            left = MiddleNode(
                                left = balances.leafNode(12),
                                right = NilNode,
                                hash = hashes[12]
                            ),
                            right = NilNode,
                            hash = hashes[12]
                        ),
                        hash = hashes[8..12]
                    ),
                    hash = hashes.all()
                )
            )
        }

        verify("Merkle tree has correct leaf nodes") {
            assertThat(tree.leafNodes).withMessage().hasSize(13)
            assertThat(tree.leafNodes.toList()).withMessage().containsExactlyInAnyOrderElementsOf(
                balances.mapIndexed { index, node ->
                    Pair(
                        Hash(node.abiEncode()),
                        LeafNode(node, Hash(node.abiEncode()), index)
                    )
                }
            )
        }
        // TODO test path
    }

    private fun List<AccountBalance>.leafNode(index: Int): LeafNode =
        LeafNode(this[index], Hash(this[index].abiEncode()), index)

    private fun List<AccountBalance>.hashes(): List<Hash> =
        this.map { Hash(it.abiEncode()) }

    private fun List<Hash>.all(): Hash =
        Hash(this.joinToString(separator = "") { it.value })

    private operator fun List<Hash>.get(range: IntRange): Hash =
        Hash(range.joinToString(separator = "") { this[it].value })
}
