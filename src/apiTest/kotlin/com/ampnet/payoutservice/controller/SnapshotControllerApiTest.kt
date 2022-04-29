package com.ampnet.payoutservice.controller

import com.ampnet.payoutservice.ControllerTestBase
import com.ampnet.payoutservice.ManualFixedScheduler
import com.ampnet.payoutservice.blockchain.SimpleERC20
import com.ampnet.payoutservice.config.TestSchedulerConfiguration
import com.ampnet.payoutservice.controller.response.CreateSnapshotResponse
import com.ampnet.payoutservice.controller.response.SnapshotResponse
import com.ampnet.payoutservice.controller.response.SnapshotsResponse
import com.ampnet.payoutservice.exception.ErrorCode
import com.ampnet.payoutservice.generated.jooq.tables.MerkleTreeLeafNode
import com.ampnet.payoutservice.generated.jooq.tables.MerkleTreeRoot
import com.ampnet.payoutservice.model.params.FetchMerkleTreeParams
import com.ampnet.payoutservice.model.result.FullSnapshot
import com.ampnet.payoutservice.model.result.FullSnapshotData
import com.ampnet.payoutservice.model.result.OtherSnapshotData
import com.ampnet.payoutservice.model.result.Snapshot
import com.ampnet.payoutservice.repository.MerkleTreeRepository
import com.ampnet.payoutservice.repository.SnapshotRepository
import com.ampnet.payoutservice.security.WithMockUser
import com.ampnet.payoutservice.service.SnapshotQueueService
import com.ampnet.payoutservice.testcontainers.HardhatTestContainer
import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.Hash
import com.ampnet.payoutservice.util.HashFunction
import com.ampnet.payoutservice.util.IpfsHash
import com.ampnet.payoutservice.util.SnapshotStatus
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
import com.ampnet.payoutservice.generated.jooq.tables.Snapshot as SnapshotTable

@Import(TestSchedulerConfiguration::class)
class SnapshotControllerApiTest : ControllerTestBase() {

    private val accounts = HardhatTestContainer.accounts

    @Autowired
    private lateinit var merkleTreeRepository: MerkleTreeRepository

    @Autowired
    private lateinit var snapshotRepository: SnapshotRepository

    @Autowired
    private lateinit var snapshotQueueService: SnapshotQueueService

    @Autowired
    private lateinit var dslContext: DSLContext

    @Autowired
    private lateinit var snapshotQueueScheduler: ManualFixedScheduler

    @BeforeEach
    fun beforeEach() {
        dslContext.deleteFrom(SnapshotTable.SNAPSHOT).execute()
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
    fun mustSuccessfullyCreateSnapshotForSomeAsset() {
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

        val name = "snapshot-name"
        val createSnapshotResponse = suppose("create snapshot request is made") {
            val response = mockMvc.perform(
                MockMvcRequestBuilders.post("/snapshots")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\n    \"name\": \"$name\",\n" +
                            "   \"chain_id\": ${chainId.value},\n " +
                            "   \"asset_address\": \"${contract.contractAddress}\",\n " +
                            "   \"payout_block_number\": \"${payoutBlock.value}\",\n " +
                            "   \"ignored_holder_addresses\": ${ignoredAddresses.map { "\"$it\"" }}\n}"
                    )
            )
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()

            objectMapper.readValue(response.response.contentAsString, CreateSnapshotResponse::class.java)
        }

        verify("snapshot is created in database") {
            val result = snapshotRepository.getById(createSnapshotResponse.id)

            assertThat(result).withMessage()
                .isNotNull()
            assertThat(result).withMessage()
                .isEqualTo(
                    Snapshot(
                        id = createSnapshotResponse.id,
                        name = name,
                        chainId = chainId,
                        assetAddress = ContractAddress(contract.contractAddress),
                        blockNumber = payoutBlock,
                        ignoredHolderAddresses = ignoredAddresses.mapTo(HashSet()) { WalletAddress(it) },
                        ownerAddress = WalletAddress(HardhatTestContainer.accountAddress1),
                        data = OtherSnapshotData(SnapshotStatus.PENDING, null)
                    )
                )
        }
    }

    @Test
    @WithMockUser
    fun mustSuccessfullyCreateAndProcessSnapshotForSomeAsset() {
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

        val name = "snapshot-name"
        val createSnapshotResponse = suppose("create snapshot request is made") {
            val response = mockMvc.perform(
                MockMvcRequestBuilders.post("/snapshots")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\n    \"name\": \"$name\",\n" +
                            "   \"chain_id\": ${chainId.value},\n " +
                            "   \"asset_address\": \"${contract.contractAddress}\",\n " +
                            "   \"payout_block_number\": \"${payoutBlock.value}\",\n " +
                            "   \"ignored_holder_addresses\": ${ignoredAddresses.map { "\"$it\"" }}\n}"
                    )
            )
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()

            objectMapper.readValue(response.response.contentAsString, CreateSnapshotResponse::class.java)
        }

        val pendingSnapshot = suppose("snapshot is fetched by ID before execution") {
            val response = mockMvc.perform(
                MockMvcRequestBuilders.get("/snapshots/${createSnapshotResponse.id}")
            )
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()

            objectMapper.readValue(response.response.contentAsString, SnapshotResponse::class.java)
        }

        verify("pending snapshot has correct payload") {
            assertThat(pendingSnapshot).withMessage()
                .isEqualTo(
                    FullSnapshot(
                        id = createSnapshotResponse.id,
                        name = name,
                        chainId = chainId,
                        assetAddress = ContractAddress(contract.contractAddress),
                        blockNumber = payoutBlock,
                        ignoredHolderAddresses = ignoredAddresses.mapTo(HashSet()) { WalletAddress(it) },
                        ownerAddress = WalletAddress(HardhatTestContainer.accountAddress1),
                        snapshotStatus = SnapshotStatus.PENDING,
                        snapshotFailureCause = null,
                        data = null
                    ).toSnapshotResponse()
                )
        }

        suppose("snapshot is processed") {
            snapshotQueueScheduler.execute()
        }

        val completedSnapshot = suppose("snapshot is fetched by ID after execution") {
            val response = mockMvc.perform(
                MockMvcRequestBuilders.get("/snapshots/${createSnapshotResponse.id}")
            )
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()

            objectMapper.readValue(response.response.contentAsString, SnapshotResponse::class.java)
        }

        verify("completed snapshot has correct payload") {
            assertThat(completedSnapshot).withMessage()
                .isEqualTo(
                    FullSnapshot(
                        id = createSnapshotResponse.id,
                        name = name,
                        chainId = chainId,
                        assetAddress = ContractAddress(contract.contractAddress),
                        blockNumber = payoutBlock,
                        ignoredHolderAddresses = ignoredAddresses.mapTo(HashSet()) { WalletAddress(it) },
                        ownerAddress = WalletAddress(HardhatTestContainer.accountAddress1),
                        snapshotStatus = SnapshotStatus.SUCCESS,
                        snapshotFailureCause = null,
                        data = FullSnapshotData(
                            totalAssetAmount = Balance(BigInteger("600")),
                            // checked in next verify block
                            merkleRootHash = Hash(completedSnapshot.assetSnapshotMerkleRoot!!),
                            merkleTreeIpfsHash = ipfsHash,
                            // checked in next verify block
                            merkleTreeDepth = completedSnapshot.assetSnapshotMerkleDepth!!,
                            hashFn = HashFunction.KECCAK_256
                        )
                    ).toSnapshotResponse()
                )
        }

        verify("Merkle tree is correctly created in the database") {
            val result = merkleTreeRepository.fetchTree(
                FetchMerkleTreeParams(
                    rootHash = Hash(completedSnapshot.assetSnapshotMerkleRoot!!),
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

            assertThat(completedSnapshot.assetSnapshotMerkleDepth).withMessage()
                .isEqualTo(result?.tree?.root?.depth)
        }
    }

    @Test
    @WithMockUser
    fun mustReturnSnapshotsForSomeChainIdAndOwner() {
        val mainAccount = accounts[0]

        val erc20Contract = suppose("simple ERC20 contract is deployed") {
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
            erc20Contract.transferAndMine(accounts[1].address, BigInteger("100"))
            erc20Contract.transferAndMine(accounts[2].address, BigInteger("200"))
            erc20Contract.transferAndMine(accounts[3].address, BigInteger("300"))
            erc20Contract.transferAndMine(accounts[4].address, BigInteger("400"))
        }

        val payoutBlock = hardhatContainer.blockNumber()

        erc20Contract.applyWeb3jFilterFix(BlockNumber(BigInteger.ZERO), payoutBlock)

        suppose("some additional transactions of ERC20 token are made") {
            erc20Contract.transferAndMine(accounts[1].address, BigInteger("900"))
            erc20Contract.transferAndMine(accounts[5].address, BigInteger("1000"))
            erc20Contract.transferAndMine(accounts[6].address, BigInteger("2000"))
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

        val name = "snapshot-name"
        val createSnapshotResponse = suppose("create snapshot request is made") {
            val response = mockMvc.perform(
                MockMvcRequestBuilders.post("/snapshots")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\n    \"name\": \"$name\",\n" +
                            "   \"chain_id\": ${chainId.value},\n " +
                            "   \"asset_address\": \"${erc20Contract.contractAddress}\",\n " +
                            "   \"payout_block_number\": \"${payoutBlock.value}\",\n " +
                            "   \"ignored_holder_addresses\": ${ignoredAddresses.map { "\"$it\"" }}\n}"
                    )
            )
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()

            objectMapper.readValue(response.response.contentAsString, CreateSnapshotResponse::class.java)
        }

        suppose("snapshot is processed") {
            snapshotQueueScheduler.execute()
        }

        val snapshot = snapshotQueueService.getSnapshotById(createSnapshotResponse.id)!!

        val statusesString = SnapshotStatus.values().joinToString(separator = ",") { it.name }
        val adminPayouts = suppose("snapshots are fetched for chainId and owner") {
            val response = mockMvc.perform(
                MockMvcRequestBuilders.get("/snapshots?status=$statusesString")
                    .queryParam("chainId", chainId.value.toString())
            )
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()

            objectMapper.readValue(response.response.contentAsString, SnapshotsResponse::class.java)
        }

        verify("correct snapshots are returned") {
            assertThat(adminPayouts).withMessage()
                .isEqualTo(SnapshotsResponse(listOf(snapshot.toSnapshotResponse())))
        }
    }

    @Test
    @WithMockUser
    fun mustBeAbleToCreateAndProcessSameSnapshotTwiceAndGetTheSameResponse() {
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

        val name1 = "snapshot-name-1"
        val createSnapshotResponse = suppose("first create snapshot request is made") {
            val response = mockMvc.perform(
                MockMvcRequestBuilders.post("/snapshots")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\n    \"name\": \"$name1\",\n" +
                            "   \"chain_id\": ${chainId.value},\n " +
                            "   \"asset_address\": \"${contract.contractAddress}\",\n " +
                            "   \"payout_block_number\": \"${payoutBlock.value}\",\n " +
                            "   \"ignored_holder_addresses\": []\n}"
                    )
            )
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()

            objectMapper.readValue(response.response.contentAsString, CreateSnapshotResponse::class.java)
        }

        val pendingSnapshot = suppose("first snapshot is fetched by ID before processing") {
            val response = mockMvc.perform(
                MockMvcRequestBuilders.get("/snapshots/${createSnapshotResponse.id}")
            )
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()

            objectMapper.readValue(response.response.contentAsString, SnapshotResponse::class.java)
        }

        verify("first pending snapshot has correct payload") {
            assertThat(pendingSnapshot).withMessage()
                .isEqualTo(
                    FullSnapshot(
                        id = createSnapshotResponse.id,
                        name = name1,
                        chainId = chainId,
                        assetAddress = ContractAddress(contract.contractAddress),
                        blockNumber = payoutBlock,
                        ignoredHolderAddresses = emptySet(),
                        ownerAddress = WalletAddress(HardhatTestContainer.accountAddress1),
                        snapshotStatus = SnapshotStatus.PENDING,
                        snapshotFailureCause = null,
                        data = null
                    ).toSnapshotResponse()
                )
        }

        suppose("first snapshot is processed") {
            snapshotQueueScheduler.execute()
        }

        val completedSnapshot = suppose("first snapshot is fetched by ID after processing") {
            val response = mockMvc.perform(
                MockMvcRequestBuilders.get("/snapshots/${createSnapshotResponse.id}")
            )
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()

            objectMapper.readValue(response.response.contentAsString, SnapshotResponse::class.java)
        }

        verify("first completed snapshot has correct payload") {
            assertThat(completedSnapshot).withMessage()
                .isEqualTo(
                    FullSnapshot(
                        id = createSnapshotResponse.id,
                        name = name1,
                        chainId = chainId,
                        assetAddress = ContractAddress(contract.contractAddress),
                        blockNumber = payoutBlock,
                        ignoredHolderAddresses = emptySet(),
                        ownerAddress = WalletAddress(HardhatTestContainer.accountAddress1),
                        snapshotStatus = SnapshotStatus.SUCCESS,
                        snapshotFailureCause = null,
                        data = FullSnapshotData(
                            totalAssetAmount = Balance(BigInteger("10000")),
                            // checked in next verify block
                            merkleRootHash = Hash(completedSnapshot.assetSnapshotMerkleRoot!!),
                            merkleTreeIpfsHash = ipfsHash,
                            merkleTreeDepth = completedSnapshot.assetSnapshotMerkleDepth!!, // checked in next verify block
                            hashFn = HashFunction.KECCAK_256
                        )
                    ).toSnapshotResponse()
                )
        }

        verify("Merkle tree is correctly created in the database") {
            val result = merkleTreeRepository.fetchTree(
                FetchMerkleTreeParams(
                    rootHash = Hash(completedSnapshot.assetSnapshotMerkleRoot!!),
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

            assertThat(completedSnapshot.assetSnapshotMerkleDepth).withMessage()
                .isEqualTo(result?.tree?.root?.depth)
        }

        val name2 = "snapshot-name-2"
        val secondCreateSnapshotResponse = suppose("second create snapshot request is made") {
            val response = mockMvc.perform(
                MockMvcRequestBuilders.post("/snapshots")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\n    \"name\": \"$name2\",\n" +
                            "   \"chain_id\": ${chainId.value},\n " +
                            "   \"asset_address\": \"${contract.contractAddress}\",\n " +
                            "   \"payout_block_number\": \"${payoutBlock.value}\",\n " +
                            "   \"ignored_holder_addresses\": []\n}"
                    )
            )
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()

            objectMapper.readValue(response.response.contentAsString, CreateSnapshotResponse::class.java)
        }

        suppose("second snapshot is processed") {
            snapshotQueueScheduler.execute()
        }

        val secondCompletedSnapshot = suppose("second snapshot is fetched by ID after processing") {
            val response = mockMvc.perform(
                MockMvcRequestBuilders.get("/snapshots/${secondCreateSnapshotResponse.id}")
            )
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()

            objectMapper.readValue(response.response.contentAsString, SnapshotResponse::class.java)
        }

        verify("second completed snapshot is has correct payload") {
            assertThat(secondCompletedSnapshot).withMessage()
                .isEqualTo(completedSnapshot.copy(id = secondCreateSnapshotResponse.id, name = name2))
        }
    }

    @Test
    @WithMockUser(HardhatTestContainer.accountAddress2)
    fun mustNotCreateSnapshotWhenRequesterIsNotAssetOwner() {
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

        verify("error is returned when non-owner account attempts to create snapshot") {
            val response = mockMvc.perform(
                MockMvcRequestBuilders.post("/snapshots")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\n    \"name\": \"snapshot-name\",\n" +
                            "   \"chain_id\": ${chainId.value},\n " +
                            "   \"asset_address\": \"${contract.contractAddress}\",\n " +
                            "   \"payout_block_number\": \"${payoutBlock.value}\",\n " +
                            "   \"ignored_holder_addresses\": []\n}"
                    )
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

        verify("error is returned when snapshot is requested for non-contract address") {
            val response = mockMvc.perform(
                MockMvcRequestBuilders.post("/snapshots")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\n    \"name\": \"snapshot-name\",\n" +
                            "   \"chain_id\": ${chainId.value},\n " +
                            "   \"asset_address\": \"${accounts[1].address}\",\n " +
                            "   \"payout_block_number\": \"${payoutBlock.value}\",\n " +
                            "   \"ignored_holder_addresses\": []\n}"
                    )
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
