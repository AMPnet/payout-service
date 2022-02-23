package com.ampnet.payoutservice.controller

import com.ampnet.payoutservice.ControllerTestBase
import com.ampnet.payoutservice.ManualFixedScheduler
import com.ampnet.payoutservice.blockchain.SimpleERC20
import com.ampnet.payoutservice.config.TestSchedulerConfiguration
import com.ampnet.payoutservice.controller.response.CreatePayoutData
import com.ampnet.payoutservice.controller.response.CreatePayoutResponse
import com.ampnet.payoutservice.controller.response.CreatePayoutTaskResponse
import com.ampnet.payoutservice.exception.ErrorCode
import com.ampnet.payoutservice.generated.jooq.tables.MerkleTreeLeafNode
import com.ampnet.payoutservice.generated.jooq.tables.MerkleTreeRoot
import com.ampnet.payoutservice.model.params.FetchMerkleTreeParams
import com.ampnet.payoutservice.model.result.CreatePayoutTask
import com.ampnet.payoutservice.model.result.OtherTaskData
import com.ampnet.payoutservice.repository.CreatePayoutTaskRepository
import com.ampnet.payoutservice.repository.MerkleTreeRepository
import com.ampnet.payoutservice.security.WithMockUser
import com.ampnet.payoutservice.testcontainers.HardhatTestContainer
import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.Hash
import com.ampnet.payoutservice.util.HashFunction
import com.ampnet.payoutservice.util.IpfsHash
import com.ampnet.payoutservice.util.TaskStatus
import com.ampnet.payoutservice.util.WalletAddress
import com.ampnet.payoutservice.wiremock.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.jooq.DSLContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.EthFilter
import org.web3j.tx.gas.DefaultGasProvider
import java.math.BigInteger
import com.ampnet.payoutservice.generated.jooq.tables.CreatePayoutTask as CreatePayoutTaskTable

@Import(TestSchedulerConfiguration::class)
class PayoutControllerApiTest : ControllerTestBase() {

    private val accounts = HardhatTestContainer.accounts

    @Autowired
    private lateinit var merkleTreeRepository: MerkleTreeRepository

    @Autowired
    private lateinit var createPayoutTaskRepository: CreatePayoutTaskRepository

    @Autowired
    private lateinit var dslContext: DSLContext

    @Autowired
    private lateinit var createPayoutTaskQueueScheduler: ManualFixedScheduler

    @BeforeEach
    fun beforeEach() {
        dslContext.deleteFrom(CreatePayoutTaskTable.CREATE_PAYOUT_TASK).execute()
        dslContext.deleteFrom(MerkleTreeLeafNode.MERKLE_TREE_LEAF_NODE).execute()
        dslContext.deleteFrom(MerkleTreeRoot.MERKLE_TREE_ROOT).execute()

        WireMock.start()
    }

    @AfterEach
    fun afterEach() {
        WireMock.stop()
    }

    @Test
    @WithMockUser
    fun mustSuccessfullyCreatePayoutTaskForSomeAsset() {
        val mainAccount = accounts[0]

        val contract = suppose("simple ERC20 contract is deployed") {
            val future = SimpleERC20.deploy(
                hardhatContainer.web3j,
                mainAccount,
                DefaultGasProvider(),
                listOf(mainAccount.address),
                listOf(BigInteger("10000")),
                mainAccount.address
            ).sendAsync()
            hardhatContainer.waitAndMine()
            future.get()
        }

        suppose("some accounts get ERC20 tokens") {
            contract.transferAndMine(accounts[1].address, BigInteger("100"))
            contract.transferAndMine(accounts[2].address, BigInteger("200"))
            contract.transferAndMine(accounts[3].address, BigInteger("300"))
            contract.transferAndMine(accounts[4].address, BigInteger("400"))
        }

        val payoutBlock = hardhatContainer.blockNumber()

        contract.applyWeb3jFilterFix(BlockNumber(BigInteger.ZERO), payoutBlock)

        suppose("some additional transactions of ERC20 token are made") {
            contract.transferAndMine(accounts[1].address, BigInteger("900"))
            contract.transferAndMine(accounts[5].address, BigInteger("1000"))
            contract.transferAndMine(accounts[6].address, BigInteger("2000"))
        }

        val ipfsHash = IpfsHash("test-hash")

        suppose("Merkle tree will be stored to IPFS") {
            WireMock.server.stubFor(
                post(urlPathEqualTo("/pinning/pinJSONToIPFS"))
                    .withHeader("pinata_api_key", equalTo("test-api-key"))
                    .withHeader("pinata_secret_api_key", equalTo("test-api-secret"))
                    .willReturn(
                        aResponse()
                            .withBody(
                                """
                                {
                                    "IpfsHash": "${ipfsHash.value}",
                                    "PinSize": 1,
                                    "Timestamp": "2022-01-01T00:00:00Z"
                                }
                                """.trimIndent()
                            )
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withStatus(200)
                    )
            )
        }

        val ignoredAddresses = setOf(mainAccount.address, accounts[4].address)

        val createPayoutResponse = suppose("create payout request is made") {
            val response = mockMvc.perform(
                MockMvcRequestBuilders.post(
                    "/payouts/${chainId.value}/${contract.contractAddress}/create"
                )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\n    \"payout_block_number\": \"${payoutBlock.value}\",\n " +
                            "   \"ignored_asset_addresses\": ${ignoredAddresses.map { "\"$it\"" }},\n " +
                            "   \"issuer_address\": \"${issuerAddress.rawValue}\"}"
                    )
            )
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()

            objectMapper.readValue(response.response.contentAsString, CreatePayoutResponse::class.java)
        }

        verify("create payout task is created in database") {
            val result = createPayoutTaskRepository.getById(createPayoutResponse.taskId)

            assertThat(result).withMessage()
                .isNotNull()
            assertThat(result).withMessage()
                .isEqualTo(
                    CreatePayoutTask(
                        taskId = createPayoutResponse.taskId,
                        chainId = chainId,
                        assetAddress = ContractAddress(contract.contractAddress),
                        blockNumber = payoutBlock,
                        ignoredAssetAddresses = ignoredAddresses.mapTo(HashSet()) { WalletAddress(it) },
                        requesterAddress = WalletAddress(HardhatTestContainer.accountAddress1),
                        issuerAddress = issuerAddress,
                        data = OtherTaskData(TaskStatus.PENDING)
                    )
                )
        }
    }

    @Test
    @WithMockUser
    fun mustSuccessfullyCreateAndExecutePayoutForSomeAsset() {
        val mainAccount = accounts[0]

        val contract = suppose("simple ERC20 contract is deployed") {
            val future = SimpleERC20.deploy(
                hardhatContainer.web3j,
                mainAccount,
                DefaultGasProvider(),
                listOf(mainAccount.address),
                listOf(BigInteger("10000")),
                mainAccount.address
            ).sendAsync()
            hardhatContainer.waitAndMine()
            future.get()
        }

        suppose("some accounts get ERC20 tokens") {
            contract.transferAndMine(accounts[1].address, BigInteger("100"))
            contract.transferAndMine(accounts[2].address, BigInteger("200"))
            contract.transferAndMine(accounts[3].address, BigInteger("300"))
            contract.transferAndMine(accounts[4].address, BigInteger("400"))
        }

        val payoutBlock = hardhatContainer.blockNumber()

        contract.applyWeb3jFilterFix(BlockNumber(BigInteger.ZERO), payoutBlock)

        suppose("some additional transactions of ERC20 token are made") {
            contract.transferAndMine(accounts[1].address, BigInteger("900"))
            contract.transferAndMine(accounts[5].address, BigInteger("1000"))
            contract.transferAndMine(accounts[6].address, BigInteger("2000"))
        }

        val ipfsHash = IpfsHash("test-hash")

        suppose("Merkle tree will be stored to IPFS") {
            WireMock.server.stubFor(
                post(urlPathEqualTo("/pinning/pinJSONToIPFS"))
                    .withHeader("pinata_api_key", equalTo("test-api-key"))
                    .withHeader("pinata_secret_api_key", equalTo("test-api-secret"))
                    .willReturn(
                        aResponse()
                            .withBody(
                                """
                                {
                                    "IpfsHash": "${ipfsHash.value}",
                                    "PinSize": 1,
                                    "Timestamp": "2022-01-01T00:00:00Z"
                                }
                                """.trimIndent()
                            )
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withStatus(200)
                    )
            )
        }

        val ignoredAddresses = setOf(mainAccount.address, accounts[4].address)

        val createPayoutResponse = suppose("create payout request is made") {
            val response = mockMvc.perform(
                MockMvcRequestBuilders.post(
                    "/payouts/${chainId.value}/${contract.contractAddress}/create"
                )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\n    \"payout_block_number\": \"${payoutBlock.value}\",\n " +
                            "   \"ignored_asset_addresses\": ${ignoredAddresses.map { "\"$it\"" }},\n " +
                            "   \"issuer_address\": \"${issuerAddress.rawValue}\"}"
                    )
            )
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()

            objectMapper.readValue(response.response.contentAsString, CreatePayoutResponse::class.java)
        }

        val pendingTask = suppose("create payout task is fetched by ID before execution") {
            val response = mockMvc.perform(MockMvcRequestBuilders.get("/payouts/tasks/${createPayoutResponse.taskId}"))
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()

            objectMapper.readValue(response.response.contentAsString, CreatePayoutTaskResponse::class.java)
        }

        verify("pending create payout task has correct payload") {
            assertThat(pendingTask).withMessage()
                .isEqualTo(
                    CreatePayoutTaskResponse(
                        taskId = createPayoutResponse.taskId,
                        chainId = chainId.value,
                        assetAddress = contract.contractAddress,
                        payoutBlockNumber = payoutBlock.value,
                        ignoredAssetAddresses = ignoredAddresses,
                        requesterAddress = HardhatTestContainer.accountAddress1,
                        issuerAddress = issuerAddress.rawValue,
                        taskStatus = TaskStatus.PENDING,
                        data = null
                    )
                )
        }

        suppose("create payout task is executed") {
            createPayoutTaskQueueScheduler.execute()
        }

        val completedTask = suppose("create payout task is fetched by ID after execution") {
            val response = mockMvc.perform(MockMvcRequestBuilders.get("/payouts/tasks/${createPayoutResponse.taskId}"))
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()

            objectMapper.readValue(response.response.contentAsString, CreatePayoutTaskResponse::class.java)
        }

        verify("completed create payout task has correct payload") {
            assertThat(completedTask).withMessage()
                .isEqualTo(
                    CreatePayoutTaskResponse(
                        taskId = createPayoutResponse.taskId,
                        chainId = chainId.value,
                        assetAddress = contract.contractAddress,
                        payoutBlockNumber = payoutBlock.value,
                        ignoredAssetAddresses = ignoredAddresses,
                        requesterAddress = HardhatTestContainer.accountAddress1,
                        issuerAddress = issuerAddress.rawValue,
                        taskStatus = TaskStatus.SUCCESS,
                        data = CreatePayoutData(
                            totalAssetAmount = BigInteger("600"),
                            merkleRootHash = completedTask.data?.merkleRootHash!!, // checked in next verify block
                            merkleTreeIpfsHash = ipfsHash.value,
                            merkleTreeDepth = completedTask.data?.merkleTreeDepth!!, // checked in next verify block
                            hashFn = HashFunction.KECCAK_256
                        )
                    )
                )
        }

        verify("Merkle tree is correctly created in the database") {
            val result = merkleTreeRepository.fetchTree(
                FetchMerkleTreeParams(
                    rootHash = Hash(completedTask.data?.merkleRootHash!!),
                    chainId = chainId,
                    assetAddress = ContractAddress(contract.contractAddress)
                )
            )

            assertThat(result).withMessage()
                .isNotNull()

            assertThat(result?.tree?.leafNodesByAddress).withMessage()
                .hasSize(3)
            assertThat(result?.tree?.leafNodesByAddress?.keys).withMessage()
                .containsExactlyInAnyOrder(
                    WalletAddress(accounts[1].address),
                    WalletAddress(accounts[2].address),
                    WalletAddress(accounts[3].address)
                )
            assertThat(result?.tree?.leafNodesByAddress?.get(WalletAddress(accounts[1].address))?.value?.data?.balance)
                .withMessage()
                .isEqualTo(Balance(BigInteger("100")))
            assertThat(result?.tree?.leafNodesByAddress?.get(WalletAddress(accounts[2].address))?.value?.data?.balance)
                .withMessage()
                .isEqualTo(Balance(BigInteger("200")))
            assertThat(result?.tree?.leafNodesByAddress?.get(WalletAddress(accounts[3].address))?.value?.data?.balance)
                .withMessage()
                .isEqualTo(Balance(BigInteger("300")))

            assertThat(completedTask.data?.merkleTreeDepth).withMessage()
                .isEqualTo(result?.tree?.root?.depth)
        }
    }

    @Test
    @WithMockUser
    fun mustBeAbleToCreateAndExecuteSamePayoutTwiceAndGetTheSameResponse() {
        val mainAccount = accounts[0]

        val contract = suppose("simple ERC20 contract is deployed") {
            val future = SimpleERC20.deploy(
                hardhatContainer.web3j,
                mainAccount,
                DefaultGasProvider(),
                listOf(mainAccount.address),
                listOf(BigInteger("10000")),
                mainAccount.address
            ).sendAsync()
            hardhatContainer.waitAndMine()
            future.get()
        }

        suppose("some accounts get ERC20 tokens") {
            contract.transferAndMine(accounts[1].address, BigInteger("100"))
            contract.transferAndMine(accounts[2].address, BigInteger("200"))
            contract.transferAndMine(accounts[3].address, BigInteger("300"))
            contract.transferAndMine(accounts[4].address, BigInteger("400"))
        }

        val payoutBlock = hardhatContainer.blockNumber()

        contract.applyWeb3jFilterFix(BlockNumber(BigInteger.ZERO), payoutBlock)

        suppose("some additional transactions of ERC20 token are made") {
            contract.transferAndMine(accounts[1].address, BigInteger("900"))
            contract.transferAndMine(accounts[5].address, BigInteger("1000"))
            contract.transferAndMine(accounts[6].address, BigInteger("2000"))
        }

        val ipfsHash = IpfsHash("test-hash")

        suppose("Merkle tree will be stored to IPFS") {
            WireMock.server.stubFor(
                post(urlPathEqualTo("/pinning/pinJSONToIPFS"))
                    .withHeader("pinata_api_key", equalTo("test-api-key"))
                    .withHeader("pinata_secret_api_key", equalTo("test-api-secret"))
                    .willReturn(
                        aResponse()
                            .withBody(
                                """
                                {
                                    "IpfsHash": "${ipfsHash.value}",
                                    "PinSize": 1,
                                    "Timestamp": "2022-01-01T00:00:00Z"
                                }
                                """.trimIndent()
                            )
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withStatus(200)
                    )
            )
        }

        val createPayoutResponse = suppose("first create payout request is made") {
            val response = mockMvc.perform(
                MockMvcRequestBuilders.post(
                    "/payouts/${chainId.value}/${contract.contractAddress}/create"
                )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\n    \"payout_block_number\": \"${payoutBlock.value}\",\n " +
                            "   \"ignored_asset_addresses\": [],\n " +
                            "   \"issuer_address\": \"${issuerAddress.rawValue}\"}"
                    )
            )
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()

            objectMapper.readValue(response.response.contentAsString, CreatePayoutResponse::class.java)
        }

        val pendingTask = suppose("first create payout task is fetched by ID before execution") {
            val response = mockMvc.perform(MockMvcRequestBuilders.get("/payouts/tasks/${createPayoutResponse.taskId}"))
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()

            objectMapper.readValue(response.response.contentAsString, CreatePayoutTaskResponse::class.java)
        }

        verify("first pending create payout task has correct payload") {
            assertThat(pendingTask).withMessage()
                .isEqualTo(
                    CreatePayoutTaskResponse(
                        taskId = createPayoutResponse.taskId,
                        chainId = chainId.value,
                        assetAddress = contract.contractAddress,
                        payoutBlockNumber = payoutBlock.value,
                        ignoredAssetAddresses = emptySet(),
                        requesterAddress = HardhatTestContainer.accountAddress1,
                        issuerAddress = issuerAddress.rawValue,
                        taskStatus = TaskStatus.PENDING,
                        data = null
                    )
                )
        }

        suppose("first create payout task is executed") {
            createPayoutTaskQueueScheduler.execute()
        }

        val completedTask = suppose("first create payout task is fetched by ID after execution") {
            val response = mockMvc.perform(MockMvcRequestBuilders.get("/payouts/tasks/${createPayoutResponse.taskId}"))
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()

            objectMapper.readValue(response.response.contentAsString, CreatePayoutTaskResponse::class.java)
        }

        verify("first completed create payout task is has correct payload") {
            assertThat(completedTask).withMessage()
                .isEqualTo(
                    CreatePayoutTaskResponse(
                        taskId = createPayoutResponse.taskId,
                        chainId = chainId.value,
                        assetAddress = contract.contractAddress,
                        payoutBlockNumber = payoutBlock.value,
                        ignoredAssetAddresses = emptySet(),
                        requesterAddress = HardhatTestContainer.accountAddress1,
                        issuerAddress = issuerAddress.rawValue,
                        taskStatus = TaskStatus.SUCCESS,
                        data = CreatePayoutData(
                            totalAssetAmount = BigInteger("10000"),
                            merkleRootHash = completedTask.data?.merkleRootHash!!, // checked in next verify block
                            merkleTreeIpfsHash = ipfsHash.value,
                            merkleTreeDepth = completedTask.data?.merkleTreeDepth!!, // checked in next verify block
                            hashFn = HashFunction.KECCAK_256
                        )
                    )
                )
        }

        verify("Merkle tree is correctly created in the database") {
            val result = merkleTreeRepository.fetchTree(
                FetchMerkleTreeParams(
                    rootHash = Hash(completedTask.data?.merkleRootHash!!),
                    chainId = chainId,
                    assetAddress = ContractAddress(contract.contractAddress)
                )
            )

            assertThat(result).withMessage()
                .isNotNull()

            assertThat(result?.tree?.leafNodesByAddress).withMessage()
                .hasSize(5)
            assertThat(result?.tree?.leafNodesByAddress?.keys).withMessage()
                .containsExactlyInAnyOrder(
                    WalletAddress(mainAccount.address),
                    WalletAddress(accounts[1].address),
                    WalletAddress(accounts[2].address),
                    WalletAddress(accounts[3].address),
                    WalletAddress(accounts[4].address)
                )
            assertThat(result?.tree?.leafNodesByAddress?.get(WalletAddress(mainAccount.address))?.value?.data?.balance)
                .withMessage()
                .isEqualTo(Balance(BigInteger("9000")))
            assertThat(result?.tree?.leafNodesByAddress?.get(WalletAddress(accounts[1].address))?.value?.data?.balance)
                .withMessage()
                .isEqualTo(Balance(BigInteger("100")))
            assertThat(result?.tree?.leafNodesByAddress?.get(WalletAddress(accounts[2].address))?.value?.data?.balance)
                .withMessage()
                .isEqualTo(Balance(BigInteger("200")))
            assertThat(result?.tree?.leafNodesByAddress?.get(WalletAddress(accounts[3].address))?.value?.data?.balance)
                .withMessage()
                .isEqualTo(Balance(BigInteger("300")))
            assertThat(result?.tree?.leafNodesByAddress?.get(WalletAddress(accounts[4].address))?.value?.data?.balance)
                .withMessage()
                .isEqualTo(Balance(BigInteger("400")))

            assertThat(completedTask.data?.merkleTreeDepth).withMessage()
                .isEqualTo(result?.tree?.root?.depth)
        }

        val secondCreatePayoutResponse = suppose("second create payout request is made") {
            val response = mockMvc.perform(
                MockMvcRequestBuilders.post(
                    "/payouts/${chainId.value}/${contract.contractAddress}/create"
                )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\n    \"payout_block_number\": \"${payoutBlock.value}\",\n " +
                            "   \"ignored_asset_addresses\": [],\n " +
                            "   \"issuer_address\": \"${issuerAddress.rawValue}\"}"
                    )
            )
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()

            objectMapper.readValue(response.response.contentAsString, CreatePayoutResponse::class.java)
        }

        suppose("second create payout task is executed") {
            createPayoutTaskQueueScheduler.execute()
        }

        val secondCompletedTask = suppose("second create payout task is fetched by ID after execution") {
            val response = mockMvc.perform(
                MockMvcRequestBuilders.get("/payouts/tasks/${secondCreatePayoutResponse.taskId}")
            )
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()

            objectMapper.readValue(response.response.contentAsString, CreatePayoutTaskResponse::class.java)
        }

        verify("second completed create payout task is has correct payload") {
            assertThat(secondCompletedTask).withMessage()
                .isEqualTo(completedTask.copy(taskId = secondCreatePayoutResponse.taskId))
        }
    }

    @Test
    @WithMockUser(HardhatTestContainer.accountAddress2)
    fun mustNotCreatePayoutTaskWhenRequesterIsNotAssetOwner() {
        val mainAccount = accounts[0]

        val contract = suppose("simple ERC20 contract is deployed") {
            val future = SimpleERC20.deploy(
                hardhatContainer.web3j,
                mainAccount,
                DefaultGasProvider(),
                listOf(mainAccount.address),
                listOf(BigInteger("10000")),
                mainAccount.address
            ).sendAsync()
            hardhatContainer.waitAndMine()
            future.get()
        }

        val payoutBlock = hardhatContainer.blockNumber()

        verify("error is returned when non-owner account attempts to create payout") {
            val response = mockMvc.perform(
                MockMvcRequestBuilders.post(
                    "/payouts/${chainId.value}/${contract.contractAddress}/create"
                )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"payout_block_number\":\"${payoutBlock.value}\",\"ignored_asset_addresses\":[]}")
            )
                .andExpect(MockMvcResultMatchers.status().isBadRequest)
                .andReturn()

            verifyResponseErrorCode(response, ErrorCode.USER_NOT_ASSET_OWNER)
        }
    }

    @Test
    @WithMockUser
    fun mustReturnCorrectErrorWhenRequestedAssetAddressIsNotAContract() {
        val payoutBlock = hardhatContainer.blockNumber()

        verify("error is returned when payout is requested for non-contract address") {
            val response = mockMvc.perform(
                MockMvcRequestBuilders.post(
                    "/payouts/${chainId.value}/${accounts[1].address}/create"
                )
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"payout_block_number\":\"${payoutBlock.value}\",\"ignored_asset_addresses\":[]}")
            )
                .andExpect(MockMvcResultMatchers.status().isBadGateway)
                .andReturn()

            verifyResponseErrorCode(response, ErrorCode.BLOCKCHAIN_CONTRACT_READ_ERROR)
        }
    }

    private fun SimpleERC20.transferAndMine(address: String, amount: BigInteger) {
        transfer(address, amount).sendAsync()
        hardhatContainer.mineUntil {
            balanceOf(address).send() == amount
        }
    }

    // This is needed to make web3j work correctly with Hardhat until https://github.com/web3j/web3j/pull/1580 is merged
    private fun SimpleERC20.applyWeb3jFilterFix(startBlock: BlockNumber?, endBlock: BlockNumber) {
        val startBlockParameter =
            startBlock?.value?.let(DefaultBlockParameter::valueOf) ?: DefaultBlockParameterName.EARLIEST
        val endBlockParameter = DefaultBlockParameter.valueOf(endBlock.value)

        repeat(15) {
            hardhatContainer.web3j.ethNewFilter(
                EthFilter(startBlockParameter, endBlockParameter, contractAddress)
            ).send()
        }
    }
}
