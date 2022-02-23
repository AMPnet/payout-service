package com.ampnet.payoutservice.controller

import com.ampnet.payoutservice.TestBase
import com.ampnet.payoutservice.controller.response.FetchMerkleTreePathResponse
import com.ampnet.payoutservice.controller.response.FetchMerkleTreeResponse
import com.ampnet.payoutservice.exception.ResourceNotFoundException
import com.ampnet.payoutservice.model.params.FetchMerkleTreeParams
import com.ampnet.payoutservice.model.params.FetchMerkleTreePathParams
import com.ampnet.payoutservice.model.result.MerkleTreeWithId
import com.ampnet.payoutservice.repository.MerkleTreeRepository
import com.ampnet.payoutservice.util.AccountBalance
import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.Hash
import com.ampnet.payoutservice.util.HashFunction
import com.ampnet.payoutservice.util.MerkleTree
import com.ampnet.payoutservice.util.WalletAddress
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.kotlin.any
import org.mockito.kotlin.given
import org.mockito.kotlin.mock
import org.springframework.http.ResponseEntity
import java.math.BigInteger
import java.util.UUID

class PayoutInfoControllerTest : TestBase() {

    @Test
    fun mustCorrectlyFetchPayoutTree() {
        val repository = mock<MerkleTreeRepository>()
        val tree = MerkleTree(
            listOf(AccountBalance(WalletAddress("a"), Balance(BigInteger.ONE))),
            HashFunction.IDENTITY
        )
        val params = FetchMerkleTreeParams(
            rootHash = Hash("test"),
            chainId = ChainId(1L),
            assetAddress = ContractAddress("abc")
        )

        suppose("some Merkle tree is returned") {
            given(repository.fetchTree(params))
                .willReturn(tree.withRandomId())
        }

        val controller = PayoutInfoController(repository)

        verify("correct response is returned") {
            val response = controller.getPayoutTree(
                chainId = params.chainId.value,
                assetAddress = params.assetAddress.rawValue,
                rootHash = params.rootHash.value
            )
            assertThat(response).withMessage()
                .isEqualTo(ResponseEntity.ok(FetchMerkleTreeResponse(tree)))
        }
    }

    @Test
    fun mustThrowExceptionWhenFetchingNonExistentPayoutTree() {
        val repository = mock<MerkleTreeRepository>()
        val params = FetchMerkleTreeParams(
            rootHash = Hash("test"),
            chainId = ChainId(1L),
            assetAddress = ContractAddress("abc")
        )

        suppose("null is returned when fetching Merkle tree") {
            given(repository.fetchTree(params))
                .willReturn(null)
        }

        val controller = PayoutInfoController(repository)

        verify("exception is thrown") {
            assertThrows<ResourceNotFoundException>(message) {
                controller.getPayoutTree(
                    chainId = params.chainId.value,
                    assetAddress = params.assetAddress.rawValue,
                    rootHash = params.rootHash.value
                )
            }
        }
    }

    @Test
    fun mustCorrectlyFetchPayoutPathForSomeAccount() {
        val repository = mock<MerkleTreeRepository>()
        val accountBalance = AccountBalance(WalletAddress("def"), Balance(BigInteger.ONE))
        val params = FetchMerkleTreePathParams(
            rootHash = Hash("test"),
            chainId = ChainId(1L),
            assetAddress = ContractAddress("abc"),
            walletAddress = accountBalance.address
        )

        suppose("request address is contained in some Merkle tree") {
            given(repository.containsAddress(params))
                .willReturn(true)
        }

        val tree = MerkleTree(
            listOf(accountBalance),
            HashFunction.IDENTITY
        )

        suppose("some Merkle tree is returned") {
            given(repository.fetchTree(params.toFetchMerkleTreeParams))
                .willReturn(tree.withRandomId())
        }

        val controller = PayoutInfoController(repository)

        verify("correct response is returned") {
            val response = controller.getPayoutPath(
                chainId = params.chainId.value,
                assetAddress = params.assetAddress.rawValue,
                rootHash = params.rootHash.value,
                walletAddress = accountBalance.address.rawValue
            )
            assertThat(response).withMessage()
                .isEqualTo(
                    ResponseEntity.ok(
                        FetchMerkleTreePathResponse(
                            accountBalance.address.rawValue,
                            accountBalance.balance.rawValue,
                            tree.pathTo(accountBalance)!!
                        )
                    )
                )
        }
    }

    @Test
    fun mustThrowExceptionWhenFetchingPayoutPathForAccountNotIncludedInPayout() {
        val repository = mock<MerkleTreeRepository>()
        val accountBalance = AccountBalance(WalletAddress("def"), Balance(BigInteger.ONE))
        val params = FetchMerkleTreePathParams(
            rootHash = Hash("test"),
            chainId = ChainId(1L),
            assetAddress = ContractAddress("abc"),
            walletAddress = accountBalance.address
        )

        suppose("request address is not contained in some Merkle tree") {
            given(repository.containsAddress(params))
                .willReturn(false)
        }

        val controller = PayoutInfoController(repository)

        verify("exception is thrown") {
            assertThrows<ResourceNotFoundException>(message) {
                controller.getPayoutPath(
                    chainId = params.chainId.value,
                    assetAddress = params.assetAddress.rawValue,
                    rootHash = params.rootHash.value,
                    walletAddress = accountBalance.address.rawValue
                )
            }
        }
    }

    @Test
    fun mustThrowExceptionWhenFetchingPayoutPathForNonExistentPayout() {
        val repository = mock<MerkleTreeRepository>()
        val accountBalance = AccountBalance(WalletAddress("def"), Balance(BigInteger.ONE))
        val params = FetchMerkleTreePathParams(
            rootHash = Hash("test"),
            chainId = ChainId(1L),
            assetAddress = ContractAddress("abc"),
            walletAddress = accountBalance.address
        )

        suppose("request address is contained in some Merkle tree") {
            given(repository.containsAddress(params))
                .willReturn(true)
        }

        suppose("null is returned when fetching Merkle tree") {
            given(repository.fetchTree(params.toFetchMerkleTreeParams))
                .willReturn(null)
        }

        val controller = PayoutInfoController(repository)

        verify("exception is thrown") {
            assertThrows<ResourceNotFoundException>(message) {
                controller.getPayoutPath(
                    chainId = params.chainId.value,
                    assetAddress = params.assetAddress.rawValue,
                    rootHash = params.rootHash.value,
                    walletAddress = accountBalance.address.rawValue
                )
            }
        }
    }

    @Test
    fun mustThrowExceptionWhenPayoutPathDoesNotExist() {
        val repository = mock<MerkleTreeRepository>()
        val accountBalance = AccountBalance(WalletAddress("def"), Balance(BigInteger.ONE))
        val params = FetchMerkleTreePathParams(
            rootHash = Hash("test"),
            chainId = ChainId(1L),
            assetAddress = ContractAddress("abc"),
            walletAddress = accountBalance.address
        )

        suppose("some address is contained in some Merkle tree") {
            given(repository.containsAddress(any()))
                .willReturn(true)
        }

        val tree = MerkleTree(
            listOf(accountBalance),
            HashFunction.IDENTITY
        )

        suppose("some Merkle tree is returned") {
            given(repository.fetchTree(params.toFetchMerkleTreeParams))
                .willReturn(tree.withRandomId())
        }

        val controller = PayoutInfoController(repository)

        verify("exception is thrown") {
            assertThrows<ResourceNotFoundException>(message) {
                controller.getPayoutPath(
                    chainId = params.chainId.value,
                    assetAddress = params.assetAddress.rawValue,
                    rootHash = params.rootHash.value,
                    walletAddress = "fff"
                )
            }
        }
    }

    private fun MerkleTree.withRandomId(): MerkleTreeWithId = MerkleTreeWithId(UUID.randomUUID(), this)
}
