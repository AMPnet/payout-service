package com.ampnet.payoutservice.controller

import com.ampnet.payoutservice.ControllerTestBase
import com.ampnet.payoutservice.ManualFixedScheduler
import com.ampnet.payoutservice.blockchain.PayoutStruct
import com.ampnet.payoutservice.blockchain.SimpleERC20
import com.ampnet.payoutservice.blockchain.SimplePayoutManager
import com.ampnet.payoutservice.blockchain.SimplePayoutService
import com.ampnet.payoutservice.config.TestSchedulerConfiguration
import com.ampnet.payoutservice.controller.response.CreateSnapshotResponse
import com.ampnet.payoutservice.controller.response.InvestorPayoutResponse
import com.ampnet.payoutservice.controller.response.InvestorPayoutsResponse
import com.ampnet.payoutservice.controller.response.PayoutResponse
import com.ampnet.payoutservice.generated.jooq.tables.MerkleTreeLeafNode
import com.ampnet.payoutservice.generated.jooq.tables.MerkleTreeRoot
import com.ampnet.payoutservice.security.WithMockUser
import com.ampnet.payoutservice.service.SnapshotQueueService
import com.ampnet.payoutservice.testcontainers.HardhatTestContainer
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.IpfsHash
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
import org.web3j.utils.Numeric
import java.math.BigInteger
import com.ampnet.payoutservice.generated.jooq.tables.Snapshot as SnapshotTable

@Import(TestSchedulerConfiguration::class)
class PayoutControllerApiTest : ControllerTestBase() {

    private val accounts = HardhatTestContainer.accounts

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
    @WithMockUser(HardhatTestContainer.accountAddress2)
    fun mustReturnPayoutsForSomeInvestor() {
        val mainAccount = accounts[0]

        val erc20Contract = suppose("simple ERC20 contract is deployed") {
            val future = SimpleERC20.deploy(
                hardhatContainer.web3j,
                mainAccount,
                DefaultGasProvider(),
                listOf(mainAccount.address),
                listOf(BigInteger("10000")),
                accounts[1].address
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

        val createSnapshotResponse = suppose("create snapshot request is made") {
            val response = mockMvc.perform(
                MockMvcRequestBuilders.post("/snapshots")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\n    \"name\": \"snapshot-name\",\n" +
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
        val contractPayout = PayoutStruct(
            BigInteger.ZERO,
            HardhatTestContainer.accountAddress1,
            "payout-info",
            false,
            erc20Contract.contractAddress,
            snapshot.data?.totalAssetAmount?.rawValue!!,
            snapshot.ignoredHolderAddresses.map { it.rawValue },
            Numeric.hexStringToByteArray(snapshot.data?.merkleRootHash?.value!!),
            BigInteger.valueOf(snapshot.data?.merkleTreeDepth?.toLong()!!),
            snapshot.blockNumber.value,
            snapshot.data?.merkleTreeIpfsHash?.value!!,
            ContractAddress("123456").rawValue,
            BigInteger("60000"),
            BigInteger("60000")
        )

        val payoutManagerContract = suppose("simple payout manager contract is deployed from created snapshot") {
            val future = SimplePayoutManager.deploy(
                hardhatContainer.web3j,
                mainAccount,
                DefaultGasProvider(),
                listOf(contractPayout)
            ).sendAsync()
            hardhatContainer.waitAndMine()
            future.get()
        }

        val payoutServiceContract = suppose("simple payout service contract is deployed") {
            val future = SimplePayoutService.deploy(
                hardhatContainer.web3j,
                mainAccount,
                DefaultGasProvider()
            ).sendAsync()
            hardhatContainer.waitAndMine()
            future.get()
        }

        suppose("payout service contract will return payouts for issuer") {
            payoutServiceContract.addIssuerPayouts(issuerAddress.rawValue, listOf(contractPayout.payoutId)).send()
        }

        val adminPayouts = suppose("investor payouts are fetched for issuer") {
            val response = mockMvc.perform(
                MockMvcRequestBuilders.get("/claimable_payouts")
                    .queryParam("chainId", chainId.value.toString())
                    .queryParam("assetFactories", ContractAddress("0x1").rawValue)
                    .queryParam("payoutService", payoutServiceContract.contractAddress)
                    .queryParam("payoutManager", payoutManagerContract.contractAddress)
                    .queryParam("issuer", issuerAddress.rawValue)
            )
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andReturn()

            objectMapper.readValue(response.response.contentAsString, InvestorPayoutsResponse::class.java)
        }

        verify("correct investor payouts are returned") {
            assertThat(adminPayouts).withMessage()
                .isEqualTo(
                    InvestorPayoutsResponse(
                        listOf(
                            InvestorPayoutResponse(
                                payout = PayoutResponse(
                                    payoutId = contractPayout.payoutId,
                                    payoutOwner = contractPayout.payoutOwner,
                                    payoutInfo = contractPayout.payoutInfo,
                                    isCanceled = contractPayout.isCanceled,

                                    asset = contractPayout.asset,
                                    totalAssetAmount = contractPayout.totalAssetAmount,
                                    ignoredHolderAddresses = contractPayout.ignoredHolderAddresses.toSet(),

                                    assetSnapshotMerkleRoot = snapshot.data!!.merkleRootHash.value,
                                    assetSnapshotMerkleDepth = contractPayout.assetSnapshotMerkleDepth.intValueExact(),
                                    assetSnapshotBlockNumber = contractPayout.assetSnapshotBlockNumber,
                                    assetSnapshotMerkleIpfsHash = contractPayout.assetSnapshotMerkleIpfsHash,

                                    rewardAsset = contractPayout.rewardAsset,
                                    totalRewardAmount = contractPayout.totalRewardAmount,
                                    remainingRewardAmount = contractPayout.remainingRewardAmount
                                ),
                                investor = WalletAddress(accounts[1].address).rawValue,
                                amountClaimed = BigInteger.ZERO,

                                amountClaimable = BigInteger("10000"),
                                balance = BigInteger("100"),
                                proof = adminPayouts.claimablePayouts[0].proof // checked in unit tests
                            )
                        )
                    )
                )
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
