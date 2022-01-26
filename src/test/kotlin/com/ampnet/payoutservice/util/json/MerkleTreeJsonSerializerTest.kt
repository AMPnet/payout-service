package com.ampnet.payoutservice.util.json

import com.ampnet.payoutservice.TestBase
import com.ampnet.payoutservice.util.AccountBalance
import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.HashFunction
import com.ampnet.payoutservice.util.MerkleTree
import com.ampnet.payoutservice.util.MerkleTree.Companion.NilNode
import com.ampnet.payoutservice.util.WalletAddress
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigInteger

class MerkleTreeJsonSerializerTest : TestBase() {

    private val objectMapper = jacksonObjectMapper()

    @Test
    fun mustCorrectlySerializeSimpleMerkleTree() {
        val accountBalance = AccountBalance(WalletAddress("0x0"), Balance(BigInteger("0")))
        val tree = suppose("simple Merkle tree is created") {
            MerkleTree(
                listOf(accountBalance),
                HashFunction.IDENTITY
            )
        }

        val serializedTree = suppose("simple Merkle tree is serialized to JSON") {
            objectMapper.valueToTree<JsonNode>(tree)
        }

        verify("simple Merkle tree is correctly serialized") {
            assertThat(serializedTree).withMessage().isEqualTo(
                objectMapper.readTree(
                    """
                    {
                        "depth": ${tree.root.depth},
                        "hash": "${tree.root.hash.value}",
                        "hash_fn": "${tree.hashFn.name}",
                        "left": {
                            "hash": "${accountBalance.abiEncode()}",
                            "index": 0,
                            "data": {
                                "address": "${accountBalance.address.rawValue}",
                                "balance": "${accountBalance.balance.rawValue}"
                            }
                        },
                        "right": {
                            "hash": "${NilNode.hash.value}"
                        }
                    }
                    """.trimIndent()
                )
            )
        }
    }

    @Test
    fun mustCorrectlySerializeMultiNodeMerkleTree() {
        val accountBalances = listOf(
            AccountBalance(WalletAddress("0x0"), Balance(BigInteger("0"))),
            AccountBalance(WalletAddress("0x1"), Balance(BigInteger("1"))),
            AccountBalance(WalletAddress("0x2"), Balance(BigInteger("2"))),
            AccountBalance(WalletAddress("0x3"), Balance(BigInteger("3")))
        )
        val tree = suppose("multi-node Merkle is created") {
            MerkleTree(
                accountBalances,
                HashFunction.IDENTITY
            )
        }

        val serializedTree = suppose("multi-node Merkle tree is serialized to JSON") {
            objectMapper.valueToTree<JsonNode>(tree)
        }

        verify("multi-node Merkle tree is correctly serialized") {
            assertThat(serializedTree).withMessage().isEqualTo(
                objectMapper.readTree(
                    """
                    {
                        "depth": ${tree.root.depth},
                        "hash": "${tree.root.hash.value}",
                        "hash_fn": "${tree.hashFn.name}",
                        "left": {
                            "hash": "${accountBalances[0].abiEncode() + accountBalances[1].abiEncode()}",
                            "left": {
                                "hash": "${accountBalances[0].abiEncode()}",
                                "index": 0,
                                "data": {
                                    "address": "${accountBalances[0].address.rawValue}",
                                    "balance": "${accountBalances[0].balance.rawValue}"
                                }
                            },
                            "right": {
                                "hash": "${accountBalances[1].abiEncode()}",
                                "index": 1,
                                "data": {
                                    "address": "${accountBalances[1].address.rawValue}",
                                    "balance": "${accountBalances[1].balance.rawValue}"
                                }
                            }
                        },
                        "right": {
                            "hash": "${accountBalances[2].abiEncode() + accountBalances[3].abiEncode()}",
                            "left": {
                                "hash": "${accountBalances[2].abiEncode()}",
                                "index": 2,
                                "data": {
                                    "address": "${accountBalances[2].address.rawValue}",
                                    "balance": "${accountBalances[2].balance.rawValue}"
                                }
                            },
                            "right": {
                                "hash": "${accountBalances[3].abiEncode()}",
                                "index": 3,
                                "data": {
                                    "address": "${accountBalances[3].address.rawValue}",
                                    "balance": "${accountBalances[3].balance.rawValue}"
                                }
                            }
                        }
                    }
                    """.trimIndent()
                )
            )
        }
    }
}
