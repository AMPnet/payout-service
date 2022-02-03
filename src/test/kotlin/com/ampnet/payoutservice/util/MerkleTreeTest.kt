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
            assertThrows<IllegalArgumentException>(message) {
                MerkleTree(emptyList(), HashFunction.IDENTITY)
            }
        }
    }

    @Test
    fun mustThrowExceptionForLeafNodeHashCollision() {
        verify("exception is thrown when hash collision in leaf nodes occurs") {
            assertThrows<IllegalArgumentException>(message) {
                MerkleTree(
                    listOf(
                        AccountBalance(WalletAddress("0x1"), Balance(BigInteger("0"))),
                        AccountBalance(WalletAddress("0x2"), Balance(BigInteger("0")))
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
                        AccountBalance(WalletAddress("0x1"), Balance(BigInteger("0"))),
                        AccountBalance(WalletAddress("0x1"), Balance(BigInteger("1")))
                    ),
                    HashFunction.IDENTITY
                )
            }
        }
    }

    @Test
    fun mustCorrectlyBuildMerkleTreeForSingleElement() {
        val balance = AccountBalance(WalletAddress("0x1"), Balance(BigInteger("0")))
        val tree = suppose("Merkle tree with single element is created") {
            MerkleTree(
                listOf(balance),
                HashFunction.IDENTITY
            )
        }

        verify("Merkle tree has correct structure") {
            assertThat(tree.root).withMessage().isEqualTo(
                RootNode(
                    left = NilNode,
                    right = LeafNode(balance, Hash(balance.abiEncode())),
                    hash = NilNode.hash + Hash(balance.abiEncode()),
                    depth = 1
                )
            )
        }

        verify("Merkle tree has correct leaf nodes") {
            assertThat(tree.leafNodesByHash).withMessage().hasSize(1)
            assertThat(tree.leafNodesByHash).withMessage().containsEntry(
                Hash(balance.abiEncode()), indexedLeafNode(balance, Hash(balance.abiEncode()), 1)
            )

            assertThat(tree.leafNodesByAddress).withMessage().hasSize(1)
            assertThat(tree.leafNodesByAddress).withMessage().containsEntry(
                balance.address, indexedLeafNode(balance, Hash(balance.abiEncode()), 1)
            )
        }

        verify("Merkle tree path is correct") {
            assertThat(tree.pathTo(balance)).withMessage().isEqualTo(
                listOf(NilNode.hash.l)
            )
        }

        verify("Merkle tree does not return a path for non-contained node") {
            assertThat(tree.pathTo(nonContainedBalance)).withMessage().isNull()
        }
    }

    @Test
    fun mustCorrectlyBuildMerkleTreeForTwoElements() {
        val balances = listOf(
            AccountBalance(WalletAddress("0x1"), Balance(BigInteger("0"))),
            AccountBalance(WalletAddress("0x2"), Balance(BigInteger("1")))
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
                        indexedLeafNode(node, Hash(node.abiEncode()), index)
                    )
                }
            )

            assertThat(tree.leafNodesByAddress).withMessage().hasSize(2)
            assertThat(tree.leafNodesByAddress.toList()).withMessage().containsExactlyInAnyOrderElementsOf(
                balances.mapIndexed { index, node ->
                    Pair(
                        node.address,
                        indexedLeafNode(node, Hash(node.abiEncode()), index)
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
            AccountBalance(WalletAddress("0x1"), Balance(BigInteger("0"))),
            AccountBalance(WalletAddress("0x2"), Balance(BigInteger("1"))),
            AccountBalance(WalletAddress("0x3"), Balance(BigInteger("2")))
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
                        left = NilNode, // node[nil] to index[0]
                        right = balances.leafNode(2), // node[2] to index[1]
                        hash = NilNode.hash + hashes[2]
                    ),
                    right = MiddleNode(
                        left = balances.leafNode(0), // node[0] to index[2]
                        right = balances.leafNode(1), // node[1] to index[3]
                        hash = hashes[0..1]
                    ),
                    hash = NilNode.hash + hashes[2] + hashes[0..1],
                    depth = 2
                )
            )
        }

        // node[*] to index[*] mapping
        val indexMap = mapOf(
            2 to 1,
            0 to 2,
            1 to 3
        )

        verify("Merkle tree has correct leaf nodes") {
            assertThat(tree.leafNodesByHash).withMessage().hasSize(3)
            assertThat(tree.leafNodesByHash.toList()).withMessage().containsExactlyInAnyOrderElementsOf(
                balances.mapIndexed { index, node ->
                    Pair(
                        Hash(node.abiEncode()),
                        indexedLeafNode(node, Hash(node.abiEncode()), indexMap[index]!!)
                    )
                }
            )

            assertThat(tree.leafNodesByAddress).withMessage().hasSize(3)
            assertThat(tree.leafNodesByAddress.toList()).withMessage().containsExactlyInAnyOrderElementsOf(
                balances.mapIndexed { index, node ->
                    Pair(
                        node.address,
                        indexedLeafNode(node, Hash(node.abiEncode()), indexMap[index]!!)
                    )
                }
            )
        }

        verify("Merkle tree paths are correct") {
            assertThat(tree.pathTo(balances[0])).withMessage().isEqualTo(
                listOf(hashes[1].r, (NilNode.hash + hashes[2]).l)
            )
            assertThat(tree.pathTo(balances[1])).withMessage().isEqualTo(
                listOf(hashes[0].l, (NilNode.hash + hashes[2]).l)
            )
            assertThat(tree.pathTo(balances[2])).withMessage().isEqualTo(
                listOf(NilNode.hash.l, hashes[0..1].r)
            )
        }

        verify("Merkle tree does not return a path for non-contained node") {
            assertThat(tree.pathTo(nonContainedBalance)).withMessage().isNull()
        }
    }

    @Test
    fun mustCorrectlyBuildMerkleTreeForFourElements() {
        val balances = listOf(
            AccountBalance(WalletAddress("0x1"), Balance(BigInteger("0"))),
            AccountBalance(WalletAddress("0x2"), Balance(BigInteger("1"))),
            AccountBalance(WalletAddress("0x3"), Balance(BigInteger("2"))),
            AccountBalance(WalletAddress("0x4"), Balance(BigInteger("3")))
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
                        indexedLeafNode(node, Hash(node.abiEncode()), index)
                    )
                }
            )

            assertThat(tree.leafNodesByAddress).withMessage().hasSize(4)
            assertThat(tree.leafNodesByAddress.toList()).withMessage().containsExactlyInAnyOrderElementsOf(
                balances.mapIndexed { index, node ->
                    Pair(
                        node.address,
                        indexedLeafNode(node, Hash(node.abiEncode()), index)
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
            AccountBalance(WalletAddress("0x1"), Balance(BigInteger("0"))),
            AccountBalance(WalletAddress("0x2"), Balance(BigInteger("1"))),
            AccountBalance(WalletAddress("0x3"), Balance(BigInteger("2"))),
            AccountBalance(WalletAddress("0x4"), Balance(BigInteger("3"))),
            AccountBalance(WalletAddress("0x5"), Balance(BigInteger("4"))),
            AccountBalance(WalletAddress("0x6"), Balance(BigInteger("5"))),
            AccountBalance(WalletAddress("0x7"), Balance(BigInteger("6"))),
            AccountBalance(WalletAddress("0x8"), Balance(BigInteger("7")))
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
                        indexedLeafNode(node, Hash(node.abiEncode()), index)
                    )
                }
            )

            assertThat(tree.leafNodesByAddress).withMessage().hasSize(8)
            assertThat(tree.leafNodesByAddress.toList()).withMessage().containsExactlyInAnyOrderElementsOf(
                balances.mapIndexed { index, node ->
                    Pair(
                        node.address,
                        indexedLeafNode(node, Hash(node.abiEncode()), index)
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
            AccountBalance(WalletAddress("0x1"), Balance(BigInteger("0"))),
            AccountBalance(WalletAddress("0x2"), Balance(BigInteger("1"))),
            AccountBalance(WalletAddress("0x3"), Balance(BigInteger("2"))),
            AccountBalance(WalletAddress("0x4"), Balance(BigInteger("3"))),
            AccountBalance(WalletAddress("0x5"), Balance(BigInteger("4"))),
            AccountBalance(WalletAddress("0x6"), Balance(BigInteger("5"))),
            AccountBalance(WalletAddress("0x7"), Balance(BigInteger("6"))),
            AccountBalance(WalletAddress("0x8"), Balance(BigInteger("7"))),
            AccountBalance(WalletAddress("0x9"), Balance(BigInteger("8"))),
            AccountBalance(WalletAddress("0xa"), Balance(BigInteger("9"))),
            AccountBalance(WalletAddress("0xb"), Balance(BigInteger("10"))),
            AccountBalance(WalletAddress("0xc"), Balance(BigInteger("11")))
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
                        left = NilNode, // node[nil] to index[0..3]
                        right = MiddleNode(
                            left = MiddleNode(
                                left = balances.leafNode(8), // node[8] to index[4]
                                right = balances.leafNode(9), // node[9] to index[5]
                                hash = hashes[8..9]
                            ),
                            right = MiddleNode(
                                left = balances.leafNode(10), // node[10] to index[6]
                                right = balances.leafNode(11), // node[11] to index[7]
                                hash = hashes[10..11]
                            ),
                            hash = hashes[8..11]
                        ),
                        hash = NilNode.hash + hashes[8..11]
                    ),
                    right = MiddleNode(
                        left = MiddleNode(
                            left = MiddleNode(
                                left = balances.leafNode(0), // node[0] to index[8]
                                right = balances.leafNode(1), // node[1] to index[9]
                                hash = hashes[0..1]
                            ),
                            right = MiddleNode(
                                left = balances.leafNode(2), // node[2] to index[10]
                                right = balances.leafNode(3), // node[3] to index[11]
                                hash = hashes[2..3]
                            ),
                            hash = hashes[0..3]
                        ),
                        right = MiddleNode(
                            left = MiddleNode(
                                left = balances.leafNode(4), // node[4] to index[12]
                                right = balances.leafNode(5), // node[5] to index[13]
                                hash = hashes[4..5]
                            ),
                            right = MiddleNode(
                                left = balances.leafNode(6), // node[6] to index[14]
                                right = balances.leafNode(7), // node[7] to index[15]
                                hash = hashes[6..7]
                            ),
                            hash = hashes[4..7]
                        ),
                        hash = hashes[0..7]
                    ),
                    hash = NilNode.hash + hashes[8..11] + hashes[0..7],
                    depth = 4
                )
            )
        }

        // node[*] to index[*] mapping
        val indexMap = mapOf(
            8 to 4,
            9 to 5,
            10 to 6,
            11 to 7,
            0 to 8,
            1 to 9,
            2 to 10,
            3 to 11,
            4 to 12,
            5 to 13,
            6 to 14,
            7 to 15
        )

        verify("Merkle tree has correct leaf nodes") {
            assertThat(tree.leafNodesByHash).withMessage().hasSize(12)
            assertThat(tree.leafNodesByHash.toList()).withMessage().containsExactlyInAnyOrderElementsOf(
                balances.mapIndexed { index, node ->
                    Pair(
                        Hash(node.abiEncode()),
                        indexedLeafNode(node, Hash(node.abiEncode()), indexMap[index]!!)
                    )
                }
            )

            assertThat(tree.leafNodesByAddress).withMessage().hasSize(12)
            assertThat(tree.leafNodesByAddress.toList()).withMessage().containsExactlyInAnyOrderElementsOf(
                balances.mapIndexed { index, node ->
                    Pair(
                        node.address,
                        indexedLeafNode(node, Hash(node.abiEncode()), indexMap[index]!!)
                    )
                }
            )
        }

        verify("Merkle tree paths are correct") {
            assertThat(tree.pathTo(balances[0])).withMessage().isEqualTo(
                listOf(hashes[1].r, hashes[2..3].r, hashes[4..7].r, (NilNode.hash + hashes[8..11]).l)
            )
            assertThat(tree.pathTo(balances[1])).withMessage().isEqualTo(
                listOf(hashes[0].l, hashes[2..3].r, hashes[4..7].r, (NilNode.hash + hashes[8..11]).l)
            )
            assertThat(tree.pathTo(balances[2])).withMessage().isEqualTo(
                listOf(hashes[3].r, hashes[0..1].l, hashes[4..7].r, (NilNode.hash + hashes[8..11]).l)
            )
            assertThat(tree.pathTo(balances[3])).withMessage().isEqualTo(
                listOf(hashes[2].l, hashes[0..1].l, hashes[4..7].r, (NilNode.hash + hashes[8..11]).l)
            )
            assertThat(tree.pathTo(balances[4])).withMessage().isEqualTo(
                listOf(hashes[5].r, hashes[6..7].r, hashes[0..3].l, (NilNode.hash + hashes[8..11]).l)
            )
            assertThat(tree.pathTo(balances[5])).withMessage().isEqualTo(
                listOf(hashes[4].l, hashes[6..7].r, hashes[0..3].l, (NilNode.hash + hashes[8..11]).l)
            )
            assertThat(tree.pathTo(balances[6])).withMessage().isEqualTo(
                listOf(hashes[7].r, hashes[4..5].l, hashes[0..3].l, (NilNode.hash + hashes[8..11]).l)
            )
            assertThat(tree.pathTo(balances[7])).withMessage().isEqualTo(
                listOf(hashes[6].l, hashes[4..5].l, hashes[0..3].l, (NilNode.hash + hashes[8..11]).l)
            )
            assertThat(tree.pathTo(balances[8])).withMessage().isEqualTo(
                listOf(hashes[9].r, hashes[10..11].r, NilNode.hash.l, hashes[0..7].r)
            )
            assertThat(tree.pathTo(balances[9])).withMessage().isEqualTo(
                listOf(hashes[8].l, hashes[10..11].r, NilNode.hash.l, hashes[0..7].r)
            )
            assertThat(tree.pathTo(balances[10])).withMessage().isEqualTo(
                listOf(hashes[11].r, hashes[8..9].l, NilNode.hash.l, hashes[0..7].r)
            )
            assertThat(tree.pathTo(balances[11])).withMessage().isEqualTo(
                listOf(hashes[10].l, hashes[8..9].l, NilNode.hash.l, hashes[0..7].r)
            )
        }

        verify("Merkle tree does not return a path for non-contained node") {
            assertThat(tree.pathTo(nonContainedBalance)).withMessage().isNull()
        }
    }

    @Test
    fun mustCorrectlyBuildUnbalancedMerkleTreeForOddNumberOfElements() {
        val balances = listOf(
            AccountBalance(WalletAddress("0x1"), Balance(BigInteger("0"))),
            AccountBalance(WalletAddress("0x2"), Balance(BigInteger("1"))),
            AccountBalance(WalletAddress("0x3"), Balance(BigInteger("2"))),
            AccountBalance(WalletAddress("0x4"), Balance(BigInteger("3"))),
            AccountBalance(WalletAddress("0x5"), Balance(BigInteger("4"))),
            AccountBalance(WalletAddress("0x6"), Balance(BigInteger("5"))),
            AccountBalance(WalletAddress("0x7"), Balance(BigInteger("6"))),
            AccountBalance(WalletAddress("0x8"), Balance(BigInteger("7"))),
            AccountBalance(WalletAddress("0x9"), Balance(BigInteger("8"))),
            AccountBalance(WalletAddress("0xa"), Balance(BigInteger("9"))),
            AccountBalance(WalletAddress("0xb"), Balance(BigInteger("10"))),
            AccountBalance(WalletAddress("0xc"), Balance(BigInteger("11"))),
            AccountBalance(WalletAddress("0xd"), Balance(BigInteger("12")))
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
                            left = NilNode, // node[nil] to index[0..1]
                            right = MiddleNode(
                                left = NilNode, // node[nil] to index[2]
                                right = balances.leafNode(12), // node[12] to index[3]
                                hash = NilNode.hash + hashes[12]
                            ),
                            hash = NilNode.hash + NilNode.hash + hashes[12]
                        ),
                        right = MiddleNode(
                            left = MiddleNode(
                                left = balances.leafNode(8), // node[8] to index[4]
                                right = balances.leafNode(9), // node[9] to index[5]
                                hash = hashes[8..9]
                            ),
                            right = MiddleNode(
                                left = balances.leafNode(10), // node[10] to index[6]
                                right = balances.leafNode(11), // node[11] to index[7]
                                hash = hashes[10..11]
                            ),
                            hash = hashes[8..11]
                        ),
                        hash = NilNode.hash + NilNode.hash + hashes[12] + hashes[8..11]
                    ),
                    right = MiddleNode(
                        left = MiddleNode(
                            left = MiddleNode(
                                left = balances.leafNode(0), // node[0] to index[8]
                                right = balances.leafNode(1), // node[1] to index[9]
                                hash = hashes[0..1]
                            ),
                            right = MiddleNode(
                                left = balances.leafNode(2), // node[2] to index[10]
                                right = balances.leafNode(3), // node[3] to index[11]
                                hash = hashes[2..3]
                            ),
                            hash = hashes[0..3]
                        ),
                        right = MiddleNode(
                            left = MiddleNode(
                                left = balances.leafNode(4), // node[4] to index[12]
                                right = balances.leafNode(5), // node[5] to index[13]
                                hash = hashes[4..5]
                            ),
                            right = MiddleNode(
                                left = balances.leafNode(6), // node[6] to index[14]
                                right = balances.leafNode(7), // node[7] to index[15]
                                hash = hashes[6..7]
                            ),
                            hash = hashes[4..7]
                        ),
                        hash = hashes[0..7]
                    ),
                    hash = NilNode.hash + NilNode.hash + hashes[12] + hashes[8..11] + hashes[0..7],
                    depth = 4
                )
            )
        }

        // node[*] to index[*] mapping
        val indexMap = mapOf(
            12 to 3,
            8 to 4,
            9 to 5,
            10 to 6,
            11 to 7,
            0 to 8,
            1 to 9,
            2 to 10,
            3 to 11,
            4 to 12,
            5 to 13,
            6 to 14,
            7 to 15
        )

        verify("Merkle tree has correct leaf nodes") {
            assertThat(tree.leafNodesByHash).withMessage().hasSize(13)
            assertThat(tree.leafNodesByHash.toList()).withMessage().containsExactlyInAnyOrderElementsOf(
                balances.mapIndexed { index, node ->
                    Pair(
                        Hash(node.abiEncode()),
                        indexedLeafNode(node, Hash(node.abiEncode()), indexMap[index]!!)
                    )
                }
            )

            assertThat(tree.leafNodesByAddress).withMessage().hasSize(13)
            assertThat(tree.leafNodesByAddress.toList()).withMessage().containsExactlyInAnyOrderElementsOf(
                balances.mapIndexed { index, node ->
                    Pair(
                        node.address,
                        indexedLeafNode(node, Hash(node.abiEncode()), indexMap[index]!!)
                    )
                }
            )
        }

        verify("Merkle tree paths are correct") {
            assertThat(tree.pathTo(balances[0])).withMessage().isEqualTo(
                listOf(
                    hashes[1].r,
                    hashes[2..3].r,
                    hashes[4..7].r,
                    (NilNode.hash + NilNode.hash + hashes[12] + hashes[8..11]).l
                )
            )
            assertThat(tree.pathTo(balances[1])).withMessage().isEqualTo(
                listOf(
                    hashes[0].l,
                    hashes[2..3].r,
                    hashes[4..7].r,
                    (NilNode.hash + NilNode.hash + hashes[12] + hashes[8..11]).l
                )
            )
            assertThat(tree.pathTo(balances[2])).withMessage().isEqualTo(
                listOf(
                    hashes[3].r,
                    hashes[0..1].l,
                    hashes[4..7].r,
                    (NilNode.hash + NilNode.hash + hashes[12] + hashes[8..11]).l
                )
            )
            assertThat(tree.pathTo(balances[3])).withMessage().isEqualTo(
                listOf(
                    hashes[2].l,
                    hashes[0..1].l,
                    hashes[4..7].r,
                    (NilNode.hash + NilNode.hash + hashes[12] + hashes[8..11]).l
                )
            )
            assertThat(tree.pathTo(balances[4])).withMessage().isEqualTo(
                listOf(
                    hashes[5].r,
                    hashes[6..7].r,
                    hashes[0..3].l,
                    (NilNode.hash + NilNode.hash + hashes[12] + hashes[8..11]).l
                )
            )
            assertThat(tree.pathTo(balances[5])).withMessage().isEqualTo(
                listOf(
                    hashes[4].l,
                    hashes[6..7].r,
                    hashes[0..3].l,
                    (NilNode.hash + NilNode.hash + hashes[12] + hashes[8..11]).l
                )
            )
            assertThat(tree.pathTo(balances[6])).withMessage().isEqualTo(
                listOf(
                    hashes[7].r,
                    hashes[4..5].l,
                    hashes[0..3].l,
                    (NilNode.hash + NilNode.hash + hashes[12] + hashes[8..11]).l
                )
            )
            assertThat(tree.pathTo(balances[7])).withMessage().isEqualTo(
                listOf(
                    hashes[6].l,
                    hashes[4..5].l,
                    hashes[0..3].l,
                    (NilNode.hash + NilNode.hash + hashes[12] + hashes[8..11]).l
                )
            )
            assertThat(tree.pathTo(balances[8])).withMessage().isEqualTo(
                listOf(hashes[9].r, hashes[10..11].r, (NilNode.hash + NilNode.hash + hashes[12]).l, hashes[0..7].r)
            )
            assertThat(tree.pathTo(balances[9])).withMessage().isEqualTo(
                listOf(hashes[8].l, hashes[10..11].r, (NilNode.hash + NilNode.hash + hashes[12]).l, hashes[0..7].r)
            )
            assertThat(tree.pathTo(balances[10])).withMessage().isEqualTo(
                listOf(hashes[11].r, hashes[8..9].l, (NilNode.hash + NilNode.hash + hashes[12]).l, hashes[0..7].r)
            )
            assertThat(tree.pathTo(balances[11])).withMessage().isEqualTo(
                listOf(hashes[10].l, hashes[8..9].l, (NilNode.hash + NilNode.hash + hashes[12]).l, hashes[0..7].r)
            )
            assertThat(tree.pathTo(balances[12])).withMessage().isEqualTo(
                listOf(NilNode.hash.l, NilNode.hash.l, hashes[8..11].r, hashes[0..7].r)
            )
        }

        verify("Merkle tree does not return a path for non-contained node") {
            assertThat(tree.pathTo(nonContainedBalance)).withMessage().isNull()
        }
    }

    @Test
    fun mustCorrectlyWorkWithNonTrivialHashFunctionForSingleElement() {
        val balance = AccountBalance(WalletAddress("0x1"), Balance(BigInteger("0")))
        val tree = suppose("Merkle tree with single element is created") {
            MerkleTree(
                listOf(balance),
                HashFunction.KECCAK_256
            )
        }

        verify("Merkle tree has correct structure") {
            assertThat(tree.root).withMessage().isEqualTo(
                RootNode(
                    left = NilNode,
                    right = LeafNode(balance, HashFunction.KECCAK_256(balance.abiEncode())),
                    hash = HashFunction.KECCAK_256((NilNode.hash + HashFunction.KECCAK_256(balance.abiEncode())).value),
                    depth = 1
                )
            )
        }

        verify("Merkle tree has correct leaf nodes") {
            assertThat(tree.leafNodesByHash).withMessage().hasSize(1)
            assertThat(tree.leafNodesByHash).withMessage().containsEntry(
                HashFunction.KECCAK_256(balance.abiEncode()),
                indexedLeafNode(balance, HashFunction.KECCAK_256(balance.abiEncode()), 1)
            )

            assertThat(tree.leafNodesByAddress).withMessage().hasSize(1)
            assertThat(tree.leafNodesByAddress).withMessage().containsEntry(
                balance.address, indexedLeafNode(balance, HashFunction.KECCAK_256(balance.abiEncode()), 1)
            )
        }

        verify("Merkle tree path is correct") {
            assertThat(tree.pathTo(balance)).withMessage().isEqualTo(
                listOf(NilNode.hash.l)
            )
        }

        verify("Merkle tree does not return a path for non-contained node") {
            assertThat(tree.pathTo(nonContainedBalance)).withMessage().isNull()
        }
    }

    @Test
    fun mustCorrectlyWorkWithNonTrivialHashFunctionForMultipleElements() {
        val balances = listOf(
            AccountBalance(WalletAddress("0x1"), Balance(BigInteger("0"))),
            AccountBalance(WalletAddress("0x2"), Balance(BigInteger("1"))),
            AccountBalance(WalletAddress("0x3"), Balance(BigInteger("2")))
        )
        val hashes = balances.hashes(HashFunction.KECCAK_256)
        val tree = suppose("Merkle tree with multiple elements is created") {
            MerkleTree(
                balances.shuffled(),
                HashFunction.KECCAK_256
            )
        }

        verify("Merkle tree has correct structure") {
            // ordering is according to keccak256 hashes
            assertThat(tree.root).withMessage().isEqualTo(
                RootNode(
                    left = MiddleNode(
                        left = balances.leafNode(2, HashFunction.KECCAK_256), // node[2] to index[0]
                        right = balances.leafNode(0, HashFunction.KECCAK_256), // node[0] to index[1]
                        hash = HashFunction.KECCAK_256((hashes[2] + hashes[0]).value)
                    ),
                    right = MiddleNode(
                        left = NilNode, // node[nil] to index[2]
                        right = balances.leafNode(1, HashFunction.KECCAK_256), // node[1] to index[3]
                        hash = HashFunction.KECCAK_256((NilNode.hash + hashes[1]).value)
                    ),
                    hash = HashFunction.KECCAK_256(
                        (
                            HashFunction.KECCAK_256((hashes[2] + hashes[0]).value) +
                                HashFunction.KECCAK_256((NilNode.hash + hashes[1]).value)
                            ).value
                    ),
                    depth = 2
                )
            )
        }

        // node[*] to index[*] mapping
        val indexMap = mapOf(
            2 to 0,
            0 to 1,
            1 to 3
        )

        verify("Merkle tree has correct leaf nodes") {
            assertThat(tree.leafNodesByHash).withMessage().hasSize(3)
            assertThat(tree.leafNodesByHash.toList()).withMessage().containsExactlyInAnyOrderElementsOf(
                balances.mapIndexed { index, node ->
                    Pair(
                        HashFunction.KECCAK_256(node.abiEncode()),
                        indexedLeafNode(node, HashFunction.KECCAK_256(node.abiEncode()), indexMap[index]!!)
                    )
                }
            )

            assertThat(tree.leafNodesByAddress).withMessage().hasSize(3)
            assertThat(tree.leafNodesByAddress.toList()).withMessage().containsExactlyInAnyOrderElementsOf(
                balances.mapIndexed { index, node ->
                    Pair(
                        node.address,
                        indexedLeafNode(node, HashFunction.KECCAK_256(node.abiEncode()), indexMap[index]!!)
                    )
                }
            )
        }

        verify("Merkle tree paths are correct") {
            assertThat(tree.pathTo(balances[0])).withMessage().isEqualTo(
                listOf(hashes[2].l, HashFunction.KECCAK_256((NilNode.hash + hashes[1]).value).r)
            )
            assertThat(tree.pathTo(balances[1])).withMessage().isEqualTo(
                listOf(NilNode.hash.l, HashFunction.KECCAK_256((hashes[2] + hashes[0]).value).l)
            )
            assertThat(tree.pathTo(balances[2])).withMessage().isEqualTo(
                listOf(hashes[0].r, HashFunction.KECCAK_256((NilNode.hash + hashes[1]).value).r)
            )
        }

        verify("Merkle tree does not return a path for non-contained node") {
            assertThat(tree.pathTo(nonContainedBalance)).withMessage().isNull()
        }
    }

    private fun indexedLeafNode(node: AccountBalance, hash: Hash, index: Int): IndexedValue<LeafNode> =
        IndexedValue(index, LeafNode(node, hash))

    private fun List<AccountBalance>.leafNode(index: Int, hashFn: HashFunction = HashFunction.IDENTITY): LeafNode =
        LeafNode(this[index], hashFn(this[index].abiEncode()))

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
