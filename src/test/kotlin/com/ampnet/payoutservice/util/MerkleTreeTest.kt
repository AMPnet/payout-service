package com.ampnet.payoutservice.util

import com.ampnet.payoutservice.TestBase
import com.ampnet.payoutservice.util.MerkleTree.Companion.LeafNode
import com.ampnet.payoutservice.util.MerkleTree.Companion.MiddleNode
import com.ampnet.payoutservice.util.MerkleTree.Companion.NilNode
import com.ampnet.payoutservice.util.MerkleTree.Companion.PathSegment
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
    fun mustThrowExceptionForLeafNodeAddressCollision() {
        verify("exception is thrown when address collision in leaf nodes occurs") {
            assertThrows<IllegalArgumentException>(message) {
                MerkleTree(
                    listOf(
                        AccountBalance(WalletAddress("0x0"), Balance(BigInteger("0"))),
                        AccountBalance(WalletAddress("0x0"), Balance(BigInteger("1")))
                    ),
                    HashFunction.IDENTITY
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
            assertThat(tree.leafNodesByHash).withMessage().hasSize(1)
            assertThat(tree.leafNodesByHash).withMessage().containsEntry(
                Hash(balance.abiEncode()), LeafNode(balance, Hash(balance.abiEncode()), 0)
            )

            assertThat(tree.leafNodesByAddress).withMessage().hasSize(1)
            assertThat(tree.leafNodesByAddress).withMessage().containsEntry(
                balance.address, LeafNode(balance, Hash(balance.abiEncode()), 0)
            )
        }

        verify("Merkle tree path is correct") {
            assertThat(tree.pathTo(balance)).withMessage().isEqualTo(
                listOf(NilNode.hash.r)
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
            assertThat(tree.leafNodesByHash).withMessage().hasSize(2)
            assertThat(tree.leafNodesByHash.toList()).withMessage().containsExactlyInAnyOrderElementsOf(
                balances.mapIndexed { index, node ->
                    Pair(
                        Hash(node.abiEncode()),
                        LeafNode(node, Hash(node.abiEncode()), index)
                    )
                }
            )

            assertThat(tree.leafNodesByAddress).withMessage().hasSize(2)
            assertThat(tree.leafNodesByAddress.toList()).withMessage().containsExactlyInAnyOrderElementsOf(
                balances.mapIndexed { index, node ->
                    Pair(
                        node.address,
                        LeafNode(node, Hash(node.abiEncode()), index)
                    )
                }
            )
        }

        verify("Merkle tree paths are correct") {
            assertThat(tree.pathTo(balances[0])).withMessage().isEqualTo(
                listOf(hashes[1].r)
            )
            assertThat(tree.pathTo(balances[1])).withMessage().isEqualTo(
                listOf(hashes[0].l)
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
            assertThat(tree.leafNodesByHash).withMessage().hasSize(3)
            assertThat(tree.leafNodesByHash.toList()).withMessage().containsExactlyInAnyOrderElementsOf(
                balances.mapIndexed { index, node ->
                    Pair(
                        Hash(node.abiEncode()),
                        LeafNode(node, Hash(node.abiEncode()), index)
                    )
                }
            )

            assertThat(tree.leafNodesByAddress).withMessage().hasSize(3)
            assertThat(tree.leafNodesByAddress.toList()).withMessage().containsExactlyInAnyOrderElementsOf(
                balances.mapIndexed { index, node ->
                    Pair(
                        node.address,
                        LeafNode(node, Hash(node.abiEncode()), index)
                    )
                }
            )
        }

        verify("Merkle tree paths are correct") {
            assertThat(tree.pathTo(balances[0])).withMessage().isEqualTo(
                listOf(hashes[1].r, (hashes[2] + NilNode.hash).r)
            )
            assertThat(tree.pathTo(balances[1])).withMessage().isEqualTo(
                listOf(hashes[0].l, (hashes[2] + NilNode.hash).r)
            )
            assertThat(tree.pathTo(balances[2])).withMessage().isEqualTo(
                listOf(NilNode.hash.r, hashes[0..1].l)
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
            assertThat(tree.leafNodesByHash).withMessage().hasSize(4)
            assertThat(tree.leafNodesByHash.toList()).withMessage().containsExactlyInAnyOrderElementsOf(
                balances.mapIndexed { index, node ->
                    Pair(
                        Hash(node.abiEncode()),
                        LeafNode(node, Hash(node.abiEncode()), index)
                    )
                }
            )

            assertThat(tree.leafNodesByAddress).withMessage().hasSize(4)
            assertThat(tree.leafNodesByAddress.toList()).withMessage().containsExactlyInAnyOrderElementsOf(
                balances.mapIndexed { index, node ->
                    Pair(
                        node.address,
                        LeafNode(node, Hash(node.abiEncode()), index)
                    )
                }
            )
        }

        verify("Merkle tree paths are correct") {
            assertThat(tree.pathTo(balances[0])).withMessage().isEqualTo(
                listOf(hashes[1].r, hashes[2..3].r)
            )
            assertThat(tree.pathTo(balances[1])).withMessage().isEqualTo(
                listOf(hashes[0].l, hashes[2..3].r)
            )
            assertThat(tree.pathTo(balances[2])).withMessage().isEqualTo(
                listOf(hashes[3].r, hashes[0..1].l)
            )
            assertThat(tree.pathTo(balances[3])).withMessage().isEqualTo(
                listOf(hashes[2].l, hashes[0..1].l)
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
            assertThat(tree.leafNodesByHash).withMessage().hasSize(8)
            assertThat(tree.leafNodesByHash.toList()).withMessage().containsExactlyInAnyOrderElementsOf(
                balances.mapIndexed { index, node ->
                    Pair(
                        Hash(node.abiEncode()),
                        LeafNode(node, Hash(node.abiEncode()), index)
                    )
                }
            )

            assertThat(tree.leafNodesByAddress).withMessage().hasSize(8)
            assertThat(tree.leafNodesByAddress.toList()).withMessage().containsExactlyInAnyOrderElementsOf(
                balances.mapIndexed { index, node ->
                    Pair(
                        node.address,
                        LeafNode(node, Hash(node.abiEncode()), index)
                    )
                }
            )
        }

        verify("Merkle tree paths are correct") {
            assertThat(tree.pathTo(balances[0])).withMessage().isEqualTo(
                listOf(hashes[1].r, hashes[2..3].r, hashes[4..7].r)
            )
            assertThat(tree.pathTo(balances[1])).withMessage().isEqualTo(
                listOf(hashes[0].l, hashes[2..3].r, hashes[4..7].r)
            )
            assertThat(tree.pathTo(balances[2])).withMessage().isEqualTo(
                listOf(hashes[3].r, hashes[0..1].l, hashes[4..7].r)
            )
            assertThat(tree.pathTo(balances[3])).withMessage().isEqualTo(
                listOf(hashes[2].l, hashes[0..1].l, hashes[4..7].r)
            )
            assertThat(tree.pathTo(balances[4])).withMessage().isEqualTo(
                listOf(hashes[5].r, hashes[6..7].r, hashes[0..3].l)
            )
            assertThat(tree.pathTo(balances[5])).withMessage().isEqualTo(
                listOf(hashes[4].l, hashes[6..7].r, hashes[0..3].l)
            )
            assertThat(tree.pathTo(balances[6])).withMessage().isEqualTo(
                listOf(hashes[7].r, hashes[4..5].l, hashes[0..3].l)
            )
            assertThat(tree.pathTo(balances[7])).withMessage().isEqualTo(
                listOf(hashes[6].l, hashes[4..5].l, hashes[0..3].l)
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
            assertThat(tree.leafNodesByHash).withMessage().hasSize(12)
            assertThat(tree.leafNodesByHash.toList()).withMessage().containsExactlyInAnyOrderElementsOf(
                balances.mapIndexed { index, node ->
                    Pair(
                        Hash(node.abiEncode()),
                        LeafNode(node, Hash(node.abiEncode()), index)
                    )
                }
            )

            assertThat(tree.leafNodesByAddress).withMessage().hasSize(12)
            assertThat(tree.leafNodesByAddress.toList()).withMessage().containsExactlyInAnyOrderElementsOf(
                balances.mapIndexed { index, node ->
                    Pair(
                        node.address,
                        LeafNode(node, Hash(node.abiEncode()), index)
                    )
                }
            )
        }

        verify("Merkle tree paths are correct") {
            assertThat(tree.pathTo(balances[0])).withMessage().isEqualTo(
                listOf(hashes[1].r, hashes[2..3].r, hashes[4..7].r, (hashes[8..11] + NilNode.hash).r)
            )
            assertThat(tree.pathTo(balances[1])).withMessage().isEqualTo(
                listOf(hashes[0].l, hashes[2..3].r, hashes[4..7].r, (hashes[8..11] + NilNode.hash).r)
            )
            assertThat(tree.pathTo(balances[2])).withMessage().isEqualTo(
                listOf(hashes[3].r, hashes[0..1].l, hashes[4..7].r, (hashes[8..11] + NilNode.hash).r)
            )
            assertThat(tree.pathTo(balances[3])).withMessage().isEqualTo(
                listOf(hashes[2].l, hashes[0..1].l, hashes[4..7].r, (hashes[8..11] + NilNode.hash).r)
            )
            assertThat(tree.pathTo(balances[4])).withMessage().isEqualTo(
                listOf(hashes[5].r, hashes[6..7].r, hashes[0..3].l, (hashes[8..11] + NilNode.hash).r)
            )
            assertThat(tree.pathTo(balances[5])).withMessage().isEqualTo(
                listOf(hashes[4].l, hashes[6..7].r, hashes[0..3].l, (hashes[8..11] + NilNode.hash).r)
            )
            assertThat(tree.pathTo(balances[6])).withMessage().isEqualTo(
                listOf(hashes[7].r, hashes[4..5].l, hashes[0..3].l, (hashes[8..11] + NilNode.hash).r)
            )
            assertThat(tree.pathTo(balances[7])).withMessage().isEqualTo(
                listOf(hashes[6].l, hashes[4..5].l, hashes[0..3].l, (hashes[8..11] + NilNode.hash).r)
            )
            assertThat(tree.pathTo(balances[8])).withMessage().isEqualTo(
                listOf(hashes[9].r, hashes[10..11].r, NilNode.hash.r, hashes[0..7].l)
            )
            assertThat(tree.pathTo(balances[9])).withMessage().isEqualTo(
                listOf(hashes[8].l, hashes[10..11].r, NilNode.hash.r, hashes[0..7].l)
            )
            assertThat(tree.pathTo(balances[10])).withMessage().isEqualTo(
                listOf(hashes[11].r, hashes[8..9].l, NilNode.hash.r, hashes[0..7].l)
            )
            assertThat(tree.pathTo(balances[11])).withMessage().isEqualTo(
                listOf(hashes[10].l, hashes[8..9].l, NilNode.hash.r, hashes[0..7].l)
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
            assertThat(tree.leafNodesByHash).withMessage().hasSize(13)
            assertThat(tree.leafNodesByHash.toList()).withMessage().containsExactlyInAnyOrderElementsOf(
                balances.mapIndexed { index, node ->
                    Pair(
                        Hash(node.abiEncode()),
                        LeafNode(node, Hash(node.abiEncode()), index)
                    )
                }
            )

            assertThat(tree.leafNodesByAddress).withMessage().hasSize(13)
            assertThat(tree.leafNodesByAddress.toList()).withMessage().containsExactlyInAnyOrderElementsOf(
                balances.mapIndexed { index, node ->
                    Pair(
                        node.address,
                        LeafNode(node, Hash(node.abiEncode()), index)
                    )
                }
            )
        }

        verify("Merkle tree paths are correct") {
            assertThat(tree.pathTo(balances[0])).withMessage().isEqualTo(
                listOf(hashes[1].r, hashes[2..3].r, hashes[4..7].r, (hashes[8..12] + NilNode.hash + NilNode.hash).r)
            )
            assertThat(tree.pathTo(balances[1])).withMessage().isEqualTo(
                listOf(hashes[0].l, hashes[2..3].r, hashes[4..7].r, (hashes[8..12] + NilNode.hash + NilNode.hash).r)
            )
            assertThat(tree.pathTo(balances[2])).withMessage().isEqualTo(
                listOf(hashes[3].r, hashes[0..1].l, hashes[4..7].r, (hashes[8..12] + NilNode.hash + NilNode.hash).r)
            )
            assertThat(tree.pathTo(balances[3])).withMessage().isEqualTo(
                listOf(hashes[2].l, hashes[0..1].l, hashes[4..7].r, (hashes[8..12] + NilNode.hash + NilNode.hash).r)
            )
            assertThat(tree.pathTo(balances[4])).withMessage().isEqualTo(
                listOf(hashes[5].r, hashes[6..7].r, hashes[0..3].l, (hashes[8..12] + NilNode.hash + NilNode.hash).r)
            )
            assertThat(tree.pathTo(balances[5])).withMessage().isEqualTo(
                listOf(hashes[4].l, hashes[6..7].r, hashes[0..3].l, (hashes[8..12] + NilNode.hash + NilNode.hash).r)
            )
            assertThat(tree.pathTo(balances[6])).withMessage().isEqualTo(
                listOf(hashes[7].r, hashes[4..5].l, hashes[0..3].l, (hashes[8..12] + NilNode.hash + NilNode.hash).r)
            )
            assertThat(tree.pathTo(balances[7])).withMessage().isEqualTo(
                listOf(hashes[6].l, hashes[4..5].l, hashes[0..3].l, (hashes[8..12] + NilNode.hash + NilNode.hash).r)
            )
            assertThat(tree.pathTo(balances[8])).withMessage().isEqualTo(
                listOf(hashes[9].r, hashes[10..11].r, (hashes[12] + NilNode.hash + NilNode.hash).r, hashes[0..7].l)
            )
            assertThat(tree.pathTo(balances[9])).withMessage().isEqualTo(
                listOf(hashes[8].l, hashes[10..11].r, (hashes[12] + NilNode.hash + NilNode.hash).r, hashes[0..7].l)
            )
            assertThat(tree.pathTo(balances[10])).withMessage().isEqualTo(
                listOf(hashes[11].r, hashes[8..9].l, (hashes[12] + NilNode.hash + NilNode.hash).r, hashes[0..7].l)
            )
            assertThat(tree.pathTo(balances[11])).withMessage().isEqualTo(
                listOf(hashes[10].l, hashes[8..9].l, (hashes[12] + NilNode.hash + NilNode.hash).r, hashes[0..7].l)
            )
            assertThat(tree.pathTo(balances[12])).withMessage().isEqualTo(
                listOf(NilNode.hash.r, NilNode.hash.r, hashes[8..11].l, hashes[0..7].l)
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
                HashFunction.KECCAK_256
            )
        }

        verify("Merkle tree has correct structure") {
            assertThat(tree.root).withMessage().isEqualTo(
                RootNode(
                    left = LeafNode(balance, HashFunction.KECCAK_256(balance.abiEncode()), 0),
                    right = NilNode,
                    hash = HashFunction.KECCAK_256((HashFunction.KECCAK_256(balance.abiEncode()) + NilNode.hash).value),
                    depth = 1
                )
            )
        }

        verify("Merkle tree has correct leaf nodes") {
            assertThat(tree.leafNodesByHash).withMessage().hasSize(1)
            assertThat(tree.leafNodesByHash).withMessage().containsEntry(
                HashFunction.KECCAK_256(balance.abiEncode()),
                LeafNode(balance, HashFunction.KECCAK_256(balance.abiEncode()), 0)
            )

            assertThat(tree.leafNodesByAddress).withMessage().hasSize(1)
            assertThat(tree.leafNodesByAddress).withMessage().containsEntry(
                balance.address, LeafNode(balance, HashFunction.KECCAK_256(balance.abiEncode()), 0)
            )
        }

        verify("Merkle tree path is correct") {
            assertThat(tree.pathTo(balance)).withMessage().isEqualTo(
                listOf(NilNode.hash.r)
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
        val hashes = balances.hashes(HashFunction.KECCAK_256)
        val tree = suppose("Merkle tree with multiple elements is created") {
            MerkleTree(
                balances.shuffled(),
                HashFunction.KECCAK_256
            )
        }

        verify("Merkle tree has correct structure") {
            assertThat(tree.root).withMessage().isEqualTo(
                RootNode(
                    left = MiddleNode(
                        left = balances.leafNode(0, HashFunction.KECCAK_256),
                        right = balances.leafNode(1, HashFunction.KECCAK_256),
                        hash = HashFunction.KECCAK_256((hashes[0] + hashes[1]).value)
                    ),
                    right = MiddleNode(
                        left = balances.leafNode(2, HashFunction.KECCAK_256),
                        right = NilNode,
                        hash = HashFunction.KECCAK_256((hashes[2] + NilNode.hash).value)
                    ),
                    hash = HashFunction.KECCAK_256(
                        (
                            HashFunction.KECCAK_256((hashes[0] + hashes[1]).value) +
                                HashFunction.KECCAK_256((hashes[2] + NilNode.hash).value)
                            ).value
                    ),
                    depth = 2
                )
            )
        }

        verify("Merkle tree has correct leaf nodes") {
            assertThat(tree.leafNodesByHash).withMessage().hasSize(3)
            assertThat(tree.leafNodesByHash.toList()).withMessage().containsExactlyInAnyOrderElementsOf(
                balances.mapIndexed { index, node ->
                    Pair(
                        HashFunction.KECCAK_256(node.abiEncode()),
                        LeafNode(node, HashFunction.KECCAK_256(node.abiEncode()), index)
                    )
                }
            )

            assertThat(tree.leafNodesByAddress).withMessage().hasSize(3)
            assertThat(tree.leafNodesByAddress.toList()).withMessage().containsExactlyInAnyOrderElementsOf(
                balances.mapIndexed { index, node ->
                    Pair(
                        node.address,
                        LeafNode(node, HashFunction.KECCAK_256(node.abiEncode()), index)
                    )
                }
            )
        }

        verify("Merkle tree paths are correct") {
            assertThat(tree.pathTo(balances[0])).withMessage().isEqualTo(
                listOf(hashes[1].r, HashFunction.KECCAK_256((hashes[2] + NilNode.hash).value).r)
            )
            assertThat(tree.pathTo(balances[1])).withMessage().isEqualTo(
                listOf(hashes[0].l, HashFunction.KECCAK_256((hashes[2] + NilNode.hash).value).r)
            )
            assertThat(tree.pathTo(balances[2])).withMessage().isEqualTo(
                listOf(NilNode.hash.r, HashFunction.KECCAK_256((hashes[0] + hashes[1]).value).l)
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

    private val Hash.l
        get() = PathSegment(this, true)

    private val Hash.r
        get() = PathSegment(this, false)
}
