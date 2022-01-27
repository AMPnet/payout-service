package com.ampnet.payoutservice.blockchain

import com.ampnet.payoutservice.TestBase
import com.ampnet.payoutservice.testcontainers.HardhatTestContainer
import com.ampnet.payoutservice.util.AccountBalance
import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.Hash
import com.ampnet.payoutservice.util.HashFunction
import com.ampnet.payoutservice.util.MerkleTree
import com.ampnet.payoutservice.util.WalletAddress
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.web3j.tx.gas.DefaultGasProvider
import org.web3j.utils.Numeric
import java.math.BigInteger

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MerkleTreePathValidatorHashCompatibilityIntegTest : TestBase() {

    private val hardhatContainer = HardhatTestContainer()
    private lateinit var contract: MerkleTreePathValidator

    @BeforeAll
    fun beforeAll() {
        contract = suppose("Merkle tree path validator is deployed") {
            val future = MerkleTreePathValidator.deploy(
                hardhatContainer.web3j,
                HardhatTestContainer.accounts[0],
                DefaultGasProvider()
            ).sendAsync()
            hardhatContainer.waitAndMine()
            future.get()
        }
    }

    @Test
    fun mustBeCompatibleWithMerkleTreeWithSingleElement() {
        val accountBalance = AccountBalance(WalletAddress("0x0"), Balance(BigInteger("0")))
        val tree = suppose("Merkle tree with single element is created") {
            MerkleTree(
                listOf(accountBalance),
                HashFunction.KECCAK_256
            )
        }

        verify("contract call will return true for account balance contained in Merkle tree") {
            assertThat(
                contract.containsNode(
                    tree.root.hash.asEthByteArray(),
                    accountBalance.address.rawValue,
                    accountBalance.balance.rawValue,
                    accountBalance.treePath(tree)
                ).send()
            ).withMessage().isTrue()
        }

        verify("contract call will return false for account balance not contained in Merkle tree") {
            assertThat(
                contract.containsNode(
                    tree.root.hash.asEthByteArray(),
                    accountBalance.address.rawValue,
                    accountBalance.balance.rawValue + BigInteger.ONE,
                    accountBalance.treePath(tree)
                ).send()
            ).withMessage().isFalse()
        }
    }

    @Test
    fun mustBeCompatibleWithMerkleTreeWithTwoElements() {
        val accountBalances = listOf(
            AccountBalance(WalletAddress("0x0"), Balance(BigInteger("0"))),
            AccountBalance(WalletAddress("0x1"), Balance(BigInteger("1")))
        )
        val tree = suppose("Merkle tree with two elements is created") {
            MerkleTree(
                accountBalances,
                HashFunction.KECCAK_256
            )
        }

        contract.verifyContractCalls(accountBalances, tree)
    }

    @Test
    fun mustBeCompatibleWithMerkleTreeWithThreeElements() {
        val accountBalances = listOf(
            AccountBalance(WalletAddress("0x0"), Balance(BigInteger("0"))),
            AccountBalance(WalletAddress("0x1"), Balance(BigInteger("1"))),
            AccountBalance(WalletAddress("0x2"), Balance(BigInteger("2")))
        )
        val tree = suppose("Merkle tree with three elements is created") {
            MerkleTree(
                accountBalances,
                HashFunction.KECCAK_256
            )
        }

        contract.verifyContractCalls(accountBalances, tree)
    }

    @Test
    fun mustBeCompatibleWithMerkleTreeWithFourElements() {
        val accountBalances = listOf(
            AccountBalance(WalletAddress("0x0"), Balance(BigInteger("0"))),
            AccountBalance(WalletAddress("0x1"), Balance(BigInteger("1"))),
            AccountBalance(WalletAddress("0x2"), Balance(BigInteger("2"))),
            AccountBalance(WalletAddress("0x3"), Balance(BigInteger("3")))
        )
        val tree = suppose("Merkle tree with four elements is created") {
            MerkleTree(
                accountBalances,
                HashFunction.KECCAK_256
            )
        }

        contract.verifyContractCalls(accountBalances, tree)
    }

    @Test
    fun mustBeCompatibleWithBalancedMerkleTree() {
        val accountBalances = listOf(
            AccountBalance(WalletAddress("0x0"), Balance(BigInteger("0"))),
            AccountBalance(WalletAddress("0x1"), Balance(BigInteger("1"))),
            AccountBalance(WalletAddress("0x2"), Balance(BigInteger("2"))),
            AccountBalance(WalletAddress("0x3"), Balance(BigInteger("3"))),
            AccountBalance(WalletAddress("0x4"), Balance(BigInteger("4"))),
            AccountBalance(WalletAddress("0x5"), Balance(BigInteger("5"))),
            AccountBalance(WalletAddress("0x6"), Balance(BigInteger("6"))),
            AccountBalance(WalletAddress("0x7"), Balance(BigInteger("7")))
        )
        val tree = suppose("Merkle tree with 8 elements is created") {
            MerkleTree(
                accountBalances,
                HashFunction.KECCAK_256
            )
        }

        contract.verifyContractCalls(accountBalances, tree)
    }

    @Test
    fun mustBeCompatibleWithUnbalancedMerkleTreeWithEvenNumberOfElements() {
        val accountBalances = listOf(
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
        val tree = suppose("Merkle tree with 12 elements is created") {
            MerkleTree(
                accountBalances,
                HashFunction.KECCAK_256
            )
        }

        contract.verifyContractCalls(accountBalances, tree)
    }

    @Test
    fun mustBeCompatibleWithUnbalancedMerkleTreeWithOddNumberOfElements() {
        val accountBalances = listOf(
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
        val tree = suppose("Merkle tree with 13 elements is created") {
            MerkleTree(
                accountBalances,
                HashFunction.KECCAK_256
            )
        }

        contract.verifyContractCalls(accountBalances, tree)
    }

    private fun MerkleTreePathValidator.verifyContractCalls(accountBalances: List<AccountBalance>, tree: MerkleTree) {
        val contract = this

        verify("contract call will return true for account balances contained in Merkle tree") {
            accountBalances.withIndex().forEach {
                assertThat(
                    contract.containsNode(
                        tree.root.hash.asEthByteArray(),
                        it.value.address.rawValue,
                        it.value.balance.rawValue,
                        it.value.treePath(tree)
                    ).send()
                ).withIndexedMessage(it.index).isTrue()
            }
        }

        verify("contract call will return false for account balances not contained in Merkle tree") {
            accountBalances.withIndex().forEach {
                assertThat(
                    contract.containsNode(
                        tree.root.hash.asEthByteArray(),
                        it.value.address.rawValue,
                        it.value.balance.rawValue + BigInteger.ONE,
                        it.value.treePath(tree)
                    ).send()
                ).withIndexedMessage(it.index).isFalse()
            }
        }
    }

    private fun AccountBalance.treePath(tree: MerkleTree): List<MerkleTreePathValidator.PathSegment> =
        tree.pathTo(this)?.map {
            MerkleTreePathValidator.PathSegment(
                it.siblingHash.asEthByteArray(),
                it.isLeft
            )
        }!!

    private fun Hash.asEthByteArray() = Numeric.hexStringToByteArray(this.value)
}
