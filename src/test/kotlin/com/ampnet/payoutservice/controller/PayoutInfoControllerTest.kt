package com.ampnet.payoutservice.controller

import com.ampnet.payoutservice.TestBase
import com.ampnet.payoutservice.controller.request.FetchMerkleTreePathRequest
import com.ampnet.payoutservice.controller.request.FetchMerkleTreeRequest
import com.ampnet.payoutservice.controller.response.FetchMerkleTreePathResponse
import com.ampnet.payoutservice.controller.response.FetchMerkleTreeResponse
import com.ampnet.payoutservice.exception.ResourceNotFoundException
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

class PayoutInfoControllerTest : TestBase() {

    @Test
    fun mustCorrectlyFetchPayoutTree() {
        val repository = mock<MerkleTreeRepository>()
        val tree = MerkleTree(
            listOf(AccountBalance(WalletAddress("a"), Balance(BigInteger.ONE))),
            HashFunction.IDENTITY
        )
        val request = FetchMerkleTreeRequest(
            rootHash = Hash("test"),
            chainId = ChainId(1L),
            contractAddress = ContractAddress("abc")
        )

        suppose("some Merkle tree is returned") {
            given(repository.fetchTree(request))
                .willReturn(tree)
        }

        val controller = PayoutInfoController(repository)

        verify("correct response is returned") {
            val response = controller.getPayoutTree(
                chainId = request.chainId.value,
                assetAddress = request.contractAddress.rawValue,
                rootHash = request.rootHash.value
            )
            assertThat(response).withMessage()
                .isEqualTo(ResponseEntity.ok(FetchMerkleTreeResponse(tree)))
        }
    }

    @Test
    fun mustThrowExceptionWhenFetchingNonExistentPayoutTree() {
        val repository = mock<MerkleTreeRepository>()
        val request = FetchMerkleTreeRequest(
            rootHash = Hash("test"),
            chainId = ChainId(1L),
            contractAddress = ContractAddress("abc")
        )

        suppose("null is returned when fetching Merkle tree") {
            given(repository.fetchTree(request))
                .willReturn(null)
        }

        val controller = PayoutInfoController(repository)

        verify("exception is thrown") {
            assertThrows<ResourceNotFoundException>(message) {
                controller.getPayoutTree(
                    chainId = request.chainId.value,
                    assetAddress = request.contractAddress.rawValue,
                    rootHash = request.rootHash.value
                )
            }
        }
    }

    @Test
    fun mustCorrectlyFetchPayoutPathForSomeAccount() {
        val repository = mock<MerkleTreeRepository>()
        val accountBalance = AccountBalance(WalletAddress("def"), Balance(BigInteger.ONE))
        val request = FetchMerkleTreePathRequest(
            rootHash = Hash("test"),
            chainId = ChainId(1L),
            contractAddress = ContractAddress("abc"),
            walletAddress = accountBalance.address
        )

        suppose("request address is contained in some Merkle tree") {
            given(repository.containsAddress(request))
                .willReturn(true)
        }

        val tree = MerkleTree(
            listOf(accountBalance),
            HashFunction.IDENTITY
        )

        suppose("some Merkle tree is returned") {
            given(repository.fetchTree(request.toFetchMerkleTreeRequest))
                .willReturn(tree)
        }

        val controller = PayoutInfoController(repository)

        verify("correct response is returned") {
            val response = controller.getPayoutPath(
                chainId = request.chainId.value,
                assetAddress = request.contractAddress.rawValue,
                rootHash = request.rootHash.value,
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
        val request = FetchMerkleTreePathRequest(
            rootHash = Hash("test"),
            chainId = ChainId(1L),
            contractAddress = ContractAddress("abc"),
            walletAddress = accountBalance.address
        )

        suppose("request address is not contained in some Merkle tree") {
            given(repository.containsAddress(request))
                .willReturn(false)
        }

        val controller = PayoutInfoController(repository)

        verify("exception is thrown") {
            assertThrows<ResourceNotFoundException>(message) {
                controller.getPayoutPath(
                    chainId = request.chainId.value,
                    assetAddress = request.contractAddress.rawValue,
                    rootHash = request.rootHash.value,
                    walletAddress = accountBalance.address.rawValue
                )
            }
        }
    }

    @Test
    fun mustThrowExceptionWhenFetchingPayoutPathForNonExistentPayout() {
        val repository = mock<MerkleTreeRepository>()
        val accountBalance = AccountBalance(WalletAddress("def"), Balance(BigInteger.ONE))
        val request = FetchMerkleTreePathRequest(
            rootHash = Hash("test"),
            chainId = ChainId(1L),
            contractAddress = ContractAddress("abc"),
            walletAddress = accountBalance.address
        )

        suppose("request address is contained in some Merkle tree") {
            given(repository.containsAddress(request))
                .willReturn(true)
        }

        suppose("null is returned when fetching Merkle tree") {
            given(repository.fetchTree(request.toFetchMerkleTreeRequest))
                .willReturn(null)
        }

        val controller = PayoutInfoController(repository)

        verify("exception is thrown") {
            assertThrows<ResourceNotFoundException>(message) {
                controller.getPayoutPath(
                    chainId = request.chainId.value,
                    assetAddress = request.contractAddress.rawValue,
                    rootHash = request.rootHash.value,
                    walletAddress = accountBalance.address.rawValue
                )
            }
        }
    }

    @Test
    fun mustThrowExceptionWhenPayoutPathDoesNotExist() {
        val repository = mock<MerkleTreeRepository>()
        val accountBalance = AccountBalance(WalletAddress("def"), Balance(BigInteger.ONE))
        val request = FetchMerkleTreePathRequest(
            rootHash = Hash("test"),
            chainId = ChainId(1L),
            contractAddress = ContractAddress("abc"),
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
            given(repository.fetchTree(request.toFetchMerkleTreeRequest))
                .willReturn(tree)
        }

        val controller = PayoutInfoController(repository)

        verify("exception is thrown") {
            assertThrows<ResourceNotFoundException>(message) {
                controller.getPayoutPath(
                    chainId = request.chainId.value,
                    assetAddress = request.contractAddress.rawValue,
                    rootHash = request.rootHash.value,
                    walletAddress = "fff"
                )
            }
        }
    }
}
