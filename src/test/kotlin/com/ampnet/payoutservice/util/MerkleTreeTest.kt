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

    private val nonContainedBalance = AccountBalance(WalletAddress("0xffff"), Balance(BigInteger("99999999")))

    @Test
    fun mustThrowExceptionForEmptyNodeList() {
        verify("exception is thrown when building Merkle tree from empty list") {
            assertThrows<IllegalArgumentException>(message) { MerkleTree(emptyList(), HashFunction.IDENTITY) }
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
                    HashFunction.FIXED
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
                HashFunction.IDENTITY
            )
        }

        verify("Merkle tree has correct structure") {
            assertThat(tree.root).withMessage().isEqualTo(
                RootNode(
                    left = LeafNode(balance, Hash(balance.abiEncode()), 0),
                    right = NilNode,
                    hash = Hash(balance.abiEncode()) + NilNode.hash,
                    depth = 1
                )
            )
        }

        verify("Merkle tree has correct leaf nodes") {
            assertThat(tree.leafNodes).withMessage().hasSize(1)
            assertThat(tree.leafNodes).withMessage().containsEntry(
                Hash(balance.abiEncode()), LeafNode(balance, Hash(balance.abiEncode()), 0)
            )
        }

        verify("Merkle tree path is correct") {
            assertThat(tree.pathTo(balance)).withMessage().isEqualTo(
                listOf(NilNode.hash)
            )
        }

        verify("Merkle tree does not return a path for non-contained node") {
            assertThat(tree.pathTo(nonContainedBalance)).withMessage().isNull()
        }
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
                HashFunction.IDENTITY
            )
        }

        verify("Merkle tree has correct structure") {
            assertThat(tree.root).withMessage().isEqualTo(
                RootNode(
                    left = balances.leafNode(0),
                    right = balances.leafNode(1),
                    hash = hashes.all(),
                    depth = 1
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

        verify("Merkle tree paths are correct") {
            assertThat(tree.pathTo(balances[0])).withMessage().isEqualTo(
                listOf(hashes[1])
            )
            assertThat(tree.pathTo(balances[1])).withMessage().isEqualTo(
                listOf(hashes[0])
            )
        }

        verify("Merkle tree does not return a path for non-contained node") {
            assertThat(tree.pathTo(nonContainedBalance)).withMessage().isNull()
        }
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
                HashFunction.IDENTITY
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
                        hash = hashes[2] + NilNode.hash
                    ),
                    hash = hashes.all() + NilNode.hash,
                    depth = 2
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

        verify("Merkle tree paths are correct") {
            assertThat(tree.pathTo(balances[0])).withMessage().isEqualTo(
                listOf(hashes[1], hashes[2] + NilNode.hash)
            )
            assertThat(tree.pathTo(balances[1])).withMessage().isEqualTo(
                listOf(hashes[0], hashes[2] + NilNode.hash)
            )
            assertThat(tree.pathTo(balances[2])).withMessage().isEqualTo(
                listOf(NilNode.hash, hashes[0..1])
            )
        }

        verify("Merkle tree does not return a path for non-contained node") {
            assertThat(tree.pathTo(nonContainedBalance)).withMessage().isNull()
        }
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
                HashFunction.IDENTITY
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
                    hash = hashes.all(),
                    depth = 2
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

        verify("Merkle tree paths are correct") {
            assertThat(tree.pathTo(balances[0])).withMessage().isEqualTo(
                listOf(hashes[1], hashes[2..3])
            )
            assertThat(tree.pathTo(balances[1])).withMessage().isEqualTo(
                listOf(hashes[0], hashes[2..3])
            )
            assertThat(tree.pathTo(balances[2])).withMessage().isEqualTo(
                listOf(hashes[3], hashes[0..1])
            )
            assertThat(tree.pathTo(balances[3])).withMessage().isEqualTo(
                listOf(hashes[2], hashes[0..1])
            )
        }

        verify("Merkle tree does not return a path for non-contained node") {
            assertThat(tree.pathTo(nonContainedBalance)).withMessage().isNull()
        }
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
                HashFunction.IDENTITY
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
                    hash = hashes.all(),
                    depth = 3
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

        verify("Merkle tree paths are correct") {
            assertThat(tree.pathTo(balances[0])).withMessage().isEqualTo(
                listOf(hashes[1], hashes[2..3], hashes[4..7])
            )
            assertThat(tree.pathTo(balances[1])).withMessage().isEqualTo(
                listOf(hashes[0], hashes[2..3], hashes[4..7])
            )
            assertThat(tree.pathTo(balances[2])).withMessage().isEqualTo(
                listOf(hashes[3], hashes[0..1], hashes[4..7])
            )
            assertThat(tree.pathTo(balances[3])).withMessage().isEqualTo(
                listOf(hashes[2], hashes[0..1], hashes[4..7])
            )
            assertThat(tree.pathTo(balances[4])).withMessage().isEqualTo(
                listOf(hashes[5], hashes[6..7], hashes[0..3])
            )
            assertThat(tree.pathTo(balances[5])).withMessage().isEqualTo(
                listOf(hashes[4], hashes[6..7], hashes[0..3])
            )
            assertThat(tree.pathTo(balances[6])).withMessage().isEqualTo(
                listOf(hashes[7], hashes[4..5], hashes[0..3])
            )
            assertThat(tree.pathTo(balances[7])).withMessage().isEqualTo(
                listOf(hashes[6], hashes[4..5], hashes[0..3])
            )
        }

        verify("Merkle tree does not return a path for non-contained node") {
            assertThat(tree.pathTo(nonContainedBalance)).withMessage().isNull()
        }
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
                HashFunction.IDENTITY
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
                        hash = hashes[8..11] + NilNode.hash
                    ),
                    hash = hashes.all() + NilNode.hash,
                    depth = 4
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

        verify("Merkle tree paths are correct") {
            assertThat(tree.pathTo(balances[0])).withMessage().isEqualTo(
                listOf(hashes[1], hashes[2..3], hashes[4..7], hashes[8..11] + NilNode.hash)
            )
            assertThat(tree.pathTo(balances[1])).withMessage().isEqualTo(
                listOf(hashes[0], hashes[2..3], hashes[4..7], hashes[8..11] + NilNode.hash)
            )
            assertThat(tree.pathTo(balances[2])).withMessage().isEqualTo(
                listOf(hashes[3], hashes[0..1], hashes[4..7], hashes[8..11] + NilNode.hash)
            )
            assertThat(tree.pathTo(balances[3])).withMessage().isEqualTo(
                listOf(hashes[2], hashes[0..1], hashes[4..7], hashes[8..11] + NilNode.hash)
            )
            assertThat(tree.pathTo(balances[4])).withMessage().isEqualTo(
                listOf(hashes[5], hashes[6..7], hashes[0..3], hashes[8..11] + NilNode.hash)
            )
            assertThat(tree.pathTo(balances[5])).withMessage().isEqualTo(
                listOf(hashes[4], hashes[6..7], hashes[0..3], hashes[8..11] + NilNode.hash)
            )
            assertThat(tree.pathTo(balances[6])).withMessage().isEqualTo(
                listOf(hashes[7], hashes[4..5], hashes[0..3], hashes[8..11] + NilNode.hash)
            )
            assertThat(tree.pathTo(balances[7])).withMessage().isEqualTo(
                listOf(hashes[6], hashes[4..5], hashes[0..3], hashes[8..11] + NilNode.hash)
            )
            assertThat(tree.pathTo(balances[8])).withMessage().isEqualTo(
                listOf(hashes[9], hashes[10..11], NilNode.hash, hashes[0..7])
            )
            assertThat(tree.pathTo(balances[9])).withMessage().isEqualTo(
                listOf(hashes[8], hashes[10..11], NilNode.hash, hashes[0..7])
            )
            assertThat(tree.pathTo(balances[10])).withMessage().isEqualTo(
                listOf(hashes[11], hashes[8..9], NilNode.hash, hashes[0..7])
            )
            assertThat(tree.pathTo(balances[11])).withMessage().isEqualTo(
                listOf(hashes[10], hashes[8..9], NilNode.hash, hashes[0..7])
            )
        }

        verify("Merkle tree does not return a path for non-contained node") {
            assertThat(tree.pathTo(nonContainedBalance)).withMessage().isNull()
        }
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
                HashFunction.IDENTITY
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
                                hash = hashes[12] + NilNode.hash
                            ),
                            right = NilNode,
                            hash = hashes[12] + NilNode.hash + NilNode.hash
                        ),
                        hash = hashes[8..12] + NilNode.hash + NilNode.hash
                    ),
                    hash = hashes.all() + NilNode.hash + NilNode.hash,
                    depth = 4
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

        verify("Merkle tree paths are correct") {
            assertThat(tree.pathTo(balances[0])).withMessage().isEqualTo(
                listOf(hashes[1], hashes[2..3], hashes[4..7], hashes[8..12] + NilNode.hash + NilNode.hash)
            )
            assertThat(tree.pathTo(balances[1])).withMessage().isEqualTo(
                listOf(hashes[0], hashes[2..3], hashes[4..7], hashes[8..12] + NilNode.hash + NilNode.hash)
            )
            assertThat(tree.pathTo(balances[2])).withMessage().isEqualTo(
                listOf(hashes[3], hashes[0..1], hashes[4..7], hashes[8..12] + NilNode.hash + NilNode.hash)
            )
            assertThat(tree.pathTo(balances[3])).withMessage().isEqualTo(
                listOf(hashes[2], hashes[0..1], hashes[4..7], hashes[8..12] + NilNode.hash + NilNode.hash)
            )
            assertThat(tree.pathTo(balances[4])).withMessage().isEqualTo(
                listOf(hashes[5], hashes[6..7], hashes[0..3], hashes[8..12] + NilNode.hash + NilNode.hash)
            )
            assertThat(tree.pathTo(balances[5])).withMessage().isEqualTo(
                listOf(hashes[4], hashes[6..7], hashes[0..3], hashes[8..12] + NilNode.hash + NilNode.hash)
            )
            assertThat(tree.pathTo(balances[6])).withMessage().isEqualTo(
                listOf(hashes[7], hashes[4..5], hashes[0..3], hashes[8..12] + NilNode.hash + NilNode.hash)
            )
            assertThat(tree.pathTo(balances[7])).withMessage().isEqualTo(
                listOf(hashes[6], hashes[4..5], hashes[0..3], hashes[8..12] + NilNode.hash + NilNode.hash)
            )
            assertThat(tree.pathTo(balances[8])).withMessage().isEqualTo(
                listOf(hashes[9], hashes[10..11], hashes[12] + NilNode.hash + NilNode.hash, hashes[0..7])
            )
            assertThat(tree.pathTo(balances[9])).withMessage().isEqualTo(
                listOf(hashes[8], hashes[10..11], hashes[12] + NilNode.hash + NilNode.hash, hashes[0..7])
            )
            assertThat(tree.pathTo(balances[10])).withMessage().isEqualTo(
                listOf(hashes[11], hashes[8..9], hashes[12] + NilNode.hash + NilNode.hash, hashes[0..7])
            )
            assertThat(tree.pathTo(balances[11])).withMessage().isEqualTo(
                listOf(hashes[10], hashes[8..9], hashes[12] + NilNode.hash + NilNode.hash, hashes[0..7])
            )
            assertThat(tree.pathTo(balances[12])).withMessage().isEqualTo(
                listOf(NilNode.hash, NilNode.hash, hashes[8..11], hashes[0..7])
            )
        }

        verify("Merkle tree does not return a path for non-contained node") {
            assertThat(tree.pathTo(nonContainedBalance)).withMessage().isNull()
        }
    }

    @Test
    fun mustCorrectlyWorkWithNonTrivialHashFunctionForSingleElement() {
        val balance = AccountBalance(WalletAddress("0x0"), Balance(BigInteger("0")))
        val tree = suppose("Merkle tree with single element is created") {
            MerkleTree(
                listOf(balance),
                HashFunction.SIMPLE
            )
        }

        verify("Merkle tree has correct structure") {
            assertThat(tree.root).withMessage().isEqualTo(
                RootNode(
                    left = LeafNode(balance, HashFunction.SIMPLE(balance.abiEncode()), 0),
                    right = NilNode,
                    hash = HashFunction.SIMPLE((HashFunction.SIMPLE(balance.abiEncode()) + NilNode.hash).value),
                    depth = 1
                )
            )
        }

        verify("Merkle tree has correct leaf nodes") {
            assertThat(tree.leafNodes).withMessage().hasSize(1)
            assertThat(tree.leafNodes).withMessage().containsEntry(
                HashFunction.SIMPLE(balance.abiEncode()), LeafNode(balance, HashFunction.SIMPLE(balance.abiEncode()), 0)
            )
        }

        verify("Merkle tree path is correct") {
            assertThat(tree.pathTo(balance)).withMessage().isEqualTo(
                listOf(NilNode.hash)
            )
        }

        verify("Merkle tree does not return a path for non-contained node") {
            assertThat(tree.pathTo(nonContainedBalance)).withMessage().isNull()
        }
    }

    @Test
    fun mustCorrectlyWorkWithNonTrivialHashFunctionForMultipleElements() {
        val balances = listOf(
            AccountBalance(WalletAddress("0x0"), Balance(BigInteger("0"))),
            AccountBalance(WalletAddress("0x1"), Balance(BigInteger("1"))),
            AccountBalance(WalletAddress("0x2"), Balance(BigInteger("2")))
        )
        val hashes = balances.hashes(HashFunction.SIMPLE)
        val tree = suppose("Merkle tree with multiple elements is created") {
            MerkleTree(
                balances.shuffled(),
                HashFunction.SIMPLE
            )
        }

        verify("Merkle tree has correct structure") {
            assertThat(tree.root).withMessage().isEqualTo(
                RootNode(
                    left = MiddleNode(
                        left = balances.leafNode(0, HashFunction.SIMPLE),
                        right = balances.leafNode(1, HashFunction.SIMPLE),
                        hash = HashFunction.SIMPLE((hashes[0] + hashes[1]).value)
                    ),
                    right = MiddleNode(
                        left = balances.leafNode(2, HashFunction.SIMPLE),
                        right = NilNode,
                        hash = HashFunction.SIMPLE((hashes[2] + NilNode.hash).value)
                    ),
                    hash = HashFunction.SIMPLE(
                        (
                            HashFunction.SIMPLE((hashes[0] + hashes[1]).value) +
                                HashFunction.SIMPLE((hashes[2] + NilNode.hash).value)
                            ).value
                    ),
                    depth = 2
                )
            )
        }

        verify("Merkle tree has correct leaf nodes") {
            assertThat(tree.leafNodes).withMessage().hasSize(3)
            assertThat(tree.leafNodes.toList()).withMessage().containsExactlyInAnyOrderElementsOf(
                balances.mapIndexed { index, node ->
                    Pair(
                        HashFunction.SIMPLE(node.abiEncode()),
                        LeafNode(node, HashFunction.SIMPLE(node.abiEncode()), index)
                    )
                }
            )
        }

        verify("Merkle tree paths are correct") {
            assertThat(tree.pathTo(balances[0])).withMessage().isEqualTo(
                listOf(hashes[1], HashFunction.SIMPLE((hashes[2] + NilNode.hash).value))
            )
            assertThat(tree.pathTo(balances[1])).withMessage().isEqualTo(
                listOf(hashes[0], HashFunction.SIMPLE((hashes[2] + NilNode.hash).value))
            )
            assertThat(tree.pathTo(balances[2])).withMessage().isEqualTo(
                listOf(NilNode.hash, HashFunction.SIMPLE((hashes[0] + hashes[1]).value))
            )
        }

        verify("Merkle tree does not return a path for non-contained node") {
            assertThat(tree.pathTo(nonContainedBalance)).withMessage().isNull()
        }
    }

    private fun List<AccountBalance>.leafNode(index: Int, hashFn: HashFunction = HashFunction.IDENTITY): LeafNode =
        LeafNode(this[index], hashFn(this[index].abiEncode()), index)

    private fun List<AccountBalance>.hashes(hashFn: HashFunction = HashFunction.IDENTITY): List<Hash> =
        this.map { hashFn(it.abiEncode()) }

    private fun List<Hash>.all(): Hash =
        Hash(this.joinToString(separator = "") { it.value })

    private operator fun List<Hash>.get(range: IntRange): Hash =
        Hash(range.joinToString(separator = "") { this[it].value })
}
