package com.ampnet.payoutservice.controller

import com.ampnet.payoutservice.ControllerTestBase
import com.ampnet.payoutservice.blockchain.properties.Chain
import com.ampnet.payoutservice.controller.response.FetchMerkleTreePathResponse
import com.ampnet.payoutservice.controller.response.FetchMerkleTreeResponse
import com.ampnet.payoutservice.exception.ErrorCode
import com.ampnet.payoutservice.generated.jooq.tables.MerkleTreeLeafNode
import com.ampnet.payoutservice.generated.jooq.tables.MerkleTreeRoot
import com.ampnet.payoutservice.repository.MerkleTreeRepository
import com.ampnet.payoutservice.util.AccountBalance
import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.HashFunction
import com.ampnet.payoutservice.util.MerkleTree
import com.ampnet.payoutservice.util.WalletAddress
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.math.BigInteger

class PayoutInfoControllerApiTest : ControllerTestBase() {

    @Autowired
    private lateinit var merkleTreeRepository: MerkleTreeRepository

    @Autowired
    private lateinit var dslContext: DSLContext

    @BeforeEach
    fun beforeEach() {
        dslContext.deleteFrom(MerkleTreeLeafNode.MERKLE_TREE_LEAF_NODE).execute()
        dslContext.deleteFrom(MerkleTreeRoot.MERKLE_TREE_ROOT).execute()
    }

    @Test
    fun mustCorrectlyFetchPayoutTree() {
        val accountBalance = AccountBalance(WalletAddress("a"), Balance(BigInteger.ONE))
        val tree = MerkleTree(listOf(accountBalance), HashFunction.IDENTITY)
        val chainId = Chain.HARDHAT_TESTNET_LOCALHOST.id
        val contractAddress = ContractAddress("b")
        val blockNumber = BlockNumber(BigInteger.TEN)

        suppose("some Merkle tree is stored in the database") {
            merkleTreeRepository.storeTree(tree, chainId, contractAddress, blockNumber)
        }

        verify("correct Merkle tree is returned") {
            val response = mockMvc.perform(
                MockMvcRequestBuilders.get("/payout_info/${chainId.value}/${contractAddress.rawValue}/tree")
                    .param("rootHash", tree.root.hash.value)
            )
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()
            val responseTree = objectMapper.readTree(response.response.contentAsString)

            assertThat(responseTree).withMessage()
                .isEqualTo(objectMapper.valueToTree(FetchMerkleTreeResponse(tree)))
        }
    }

    @Test
    fun mustReturnCorrectErrorWhenFetchingNonExistentPayoutTree() {
        verify("error is returned for non-existent Merkle tree") {
            val response = mockMvc.perform(
                MockMvcRequestBuilders.get("/payout_info/123/0x1/tree")
                    .param("rootHash", "unknownHash")
            )
                .andExpect(MockMvcResultMatchers.status().isNotFound)
                .andReturn()

            verifyResponseErrorCode(response, ErrorCode.PAYOUT_MERKLE_TREE_NOT_FOUND)
        }
    }

    @Test
    fun mustCorrectlyFetchPayoutPathForSomeAccount() {
        val accountBalance = AccountBalance(WalletAddress("a"), Balance(BigInteger.ONE))
        val tree = MerkleTree(listOf(accountBalance), HashFunction.IDENTITY)
        val chainId = Chain.HARDHAT_TESTNET_LOCALHOST.id
        val contractAddress = ContractAddress("b")
        val blockNumber = BlockNumber(BigInteger.TEN)

        suppose("some Merkle tree is stored in the database") {
            merkleTreeRepository.storeTree(tree, chainId, contractAddress, blockNumber)
        }

        verify("correct Merkle tree path is returned") {
            val response = mockMvc.perform(
                MockMvcRequestBuilders.get("/payout_info/${chainId.value}/${contractAddress.rawValue}/path")
                    .param("rootHash", tree.root.hash.value)
                    .param("walletAddress", accountBalance.address.rawValue)
            )
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()
            val responseTree = objectMapper.readTree(response.response.contentAsString)

            assertThat(responseTree).withMessage()
                .isEqualTo(
                    objectMapper.valueToTree(
                        FetchMerkleTreePathResponse(
                            accountBalance.address.rawValue,
                            accountBalance.balance.rawValue.toString(),
                            tree.pathTo(accountBalance)!!
                        )
                    )
                )
        }
    }

    @Test
    fun mustReturnCorrectErrorWhenFetchingPayoutPathForNonExistentPayout() {
        verify("error is returned for non-existent payout") {
            val response = mockMvc.perform(
                MockMvcRequestBuilders.get("/payout_info/123/0x1/path")
                    .param("rootHash", "unknownHash")
                    .param("walletAddress", "0x2")
            )
                .andExpect(MockMvcResultMatchers.status().isNotFound)
                .andReturn()

            verifyResponseErrorCode(response, ErrorCode.PAYOUT_NOT_FOUND_FOR_ACCOUNT)
        }
    }

    @Test
    fun mustReturnCorrectErrorWhenFetchingPayoutPathForAccountNotIncludedInPayout() {
        val payoutAccountBalance = AccountBalance(WalletAddress("c"), Balance(BigInteger.TEN))
        val tree = MerkleTree(listOf(payoutAccountBalance), HashFunction.IDENTITY)
        val chainId = Chain.HARDHAT_TESTNET_LOCALHOST.id
        val contractAddress = ContractAddress("b")
        val blockNumber = BlockNumber(BigInteger.TEN)

        suppose("some Merkle tree is stored in the database") {
            merkleTreeRepository.storeTree(tree, chainId, contractAddress, blockNumber)
        }

        val requestAccountBalance = AccountBalance(WalletAddress("a"), Balance(BigInteger.ONE))

        verify("correct Merkle tree path is returned") {
            val response = mockMvc.perform(
                MockMvcRequestBuilders.get("/payout_info/${chainId.value}/${contractAddress.rawValue}/path")
                    .param("rootHash", tree.root.hash.value)
                    .param("walletAddress", requestAccountBalance.address.rawValue)
            )
                .andExpect(MockMvcResultMatchers.status().isNotFound)
                .andReturn()

            verifyResponseErrorCode(response, ErrorCode.PAYOUT_NOT_FOUND_FOR_ACCOUNT)
        }
    }
}
