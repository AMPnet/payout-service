package com.ampnet.payoutservice.blockchain

import com.ampnet.payoutservice.TestBase
import com.ampnet.payoutservice.blockchain.IPayoutService.PayoutStateForInvestor
import com.ampnet.payoutservice.blockchain.properties.Chain
import com.ampnet.payoutservice.config.ApplicationProperties
import com.ampnet.payoutservice.exception.InternalException
import com.ampnet.payoutservice.model.params.GetPayoutsForAdminParams
import com.ampnet.payoutservice.model.params.GetPayoutsForInvestorParams
import com.ampnet.payoutservice.model.result.Payout
import com.ampnet.payoutservice.model.result.PayoutForInvestor
import com.ampnet.payoutservice.testcontainers.HardhatTestContainer
import com.ampnet.payoutservice.util.AccountBalance
import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.Hash
import com.ampnet.payoutservice.util.HashFunction
import com.ampnet.payoutservice.util.WalletAddress
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.RemoteCall
import org.web3j.protocol.core.methods.request.EthFilter
import org.web3j.tx.gas.DefaultGasProvider
import org.web3j.utils.Numeric
import java.math.BigInteger

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Web3jBlockchainServiceIntegTest : TestBase() {

    private val hardhatContainer = HardhatTestContainer()
    private val accounts = HardhatTestContainer.accounts

    @Test
    fun mustCorrectlyFetchBalancesBasedOnBlockRange() {
        val mainAccount = accounts[0]

        val contract = suppose("simple ERC20 contract is deployed") {
            SimpleERC20.deploy(
                hardhatContainer.web3j,
                mainAccount,
                DefaultGasProvider(),
                listOf(mainAccount.address),
                listOf(BigInteger("10000")),
                mainAccount.address
            ).sendAndMine()
        }

        suppose("some accounts get ERC20 tokens") {
            contract.transferAndMine(accounts[1].address, BigInteger("100"))
            contract.transferAndMine(accounts[2].address, BigInteger("200"))
            contract.transferAndMine(accounts[3].address, BigInteger("300"))
            contract.transferAndMine(accounts[4].address, BigInteger("400"))
        }

        val startBlock = BlockNumber(BigInteger.ZERO)
        val endBlock1 = hardhatContainer.blockNumber()

        contract.applyWeb3jFilterFix(startBlock, endBlock1)

        suppose("some additional transactions of ERC20 token are made") {
            contract.transferAndMine(accounts[1].address, BigInteger("900"))
            contract.transferAndMine(accounts[5].address, BigInteger("1000"))
            contract.transferAndMine(accounts[6].address, BigInteger("2000"))
        }

        verify("correct balances are fetched for first end block") {
            val service = Web3jBlockchainService(hardhatProperties())
            val balances = service.fetchErc20AccountBalances(
                chainId = Chain.HARDHAT_TESTNET.id,
                erc20ContractAddress = ContractAddress(contract.contractAddress),
                ignoredErc20Addresses = emptySet(),
                startBlock = startBlock,
                endBlock = endBlock1
            )

            assertThat(balances).withMessage().containsExactlyInAnyOrder(
                AccountBalance(WalletAddress(mainAccount.address), Balance(BigInteger("9000"))),
                AccountBalance(WalletAddress(accounts[1].address), Balance(BigInteger("100"))),
                AccountBalance(WalletAddress(accounts[2].address), Balance(BigInteger("200"))),
                AccountBalance(WalletAddress(accounts[3].address), Balance(BigInteger("300"))),
                AccountBalance(WalletAddress(accounts[4].address), Balance(BigInteger("400")))
            )
        }

        val endBlock2 = hardhatContainer.blockNumber()

        verify("correct balances are fetched for second end block") {
            val service = Web3jBlockchainService(hardhatProperties())
            val balances = service.fetchErc20AccountBalances(
                chainId = Chain.HARDHAT_TESTNET.id,
                erc20ContractAddress = ContractAddress(contract.contractAddress),
                ignoredErc20Addresses = emptySet(),
                startBlock = startBlock,
                endBlock = endBlock2
            )

            assertThat(balances).withMessage().containsExactlyInAnyOrder(
                AccountBalance(WalletAddress(mainAccount.address), Balance(BigInteger("5100"))),
                AccountBalance(WalletAddress(accounts[1].address), Balance(BigInteger("1000"))),
                AccountBalance(WalletAddress(accounts[2].address), Balance(BigInteger("200"))),
                AccountBalance(WalletAddress(accounts[3].address), Balance(BigInteger("300"))),
                AccountBalance(WalletAddress(accounts[4].address), Balance(BigInteger("400"))),
                AccountBalance(WalletAddress(accounts[5].address), Balance(BigInteger("1000"))),
                AccountBalance(WalletAddress(accounts[6].address), Balance(BigInteger("2000")))
            )
        }
    }

    @Test
    fun mustCorrectlyFetchBalancesBasedOnBlockRangeWhenSomeAddressesAreIgnored() {
        val mainAccount = accounts[0]

        val contract = suppose("simple ERC20 contract is deployed") {
            SimpleERC20.deploy(
                hardhatContainer.web3j,
                mainAccount,
                DefaultGasProvider(),
                listOf(mainAccount.address),
                listOf(BigInteger("10000")),
                mainAccount.address
            ).sendAndMine()
        }

        suppose("some accounts get ERC20 tokens") {
            contract.transferAndMine(accounts[1].address, BigInteger("100"))
            contract.transferAndMine(accounts[2].address, BigInteger("200"))
            contract.transferAndMine(accounts[3].address, BigInteger("300"))
            contract.transferAndMine(accounts[4].address, BigInteger("400"))
        }

        val startBlock = BlockNumber(BigInteger.ZERO)
        val endBlock = hardhatContainer.blockNumber()

        contract.applyWeb3jFilterFix(startBlock, endBlock)

        suppose("some additional transactions of ERC20 token are made") {
            contract.transferAndMine(accounts[1].address, BigInteger("900"))
            contract.transferAndMine(accounts[5].address, BigInteger("1000"))
            contract.transferAndMine(accounts[6].address, BigInteger("2000"))
        }

        val ignoredAddresses = setOf(
            WalletAddress(mainAccount.address),
            WalletAddress(accounts[1].address),
            WalletAddress(accounts[3].address)
        )

        verify("correct balances are fetched") {
            val service = Web3jBlockchainService(hardhatProperties())
            val balances = service.fetchErc20AccountBalances(
                chainId = Chain.HARDHAT_TESTNET.id,
                erc20ContractAddress = ContractAddress(contract.contractAddress),
                ignoredErc20Addresses = ignoredAddresses,
                startBlock = startBlock,
                endBlock = endBlock
            )

            assertThat(balances).withMessage().containsExactlyInAnyOrder(
                AccountBalance(WalletAddress(accounts[2].address), Balance(BigInteger("200"))),
                AccountBalance(WalletAddress(accounts[4].address), Balance(BigInteger("400")))
            )
        }
    }

    @Test
    fun mustCorrectlyFetchBalancesForNullStartBlock() {
        val mainAccount = accounts[0]

        val contract = suppose("simple ERC20 contract is deployed") {
            SimpleERC20.deploy(
                hardhatContainer.web3j,
                mainAccount,
                DefaultGasProvider(),
                listOf(mainAccount.address),
                listOf(BigInteger("10000")),
                mainAccount.address
            ).sendAndMine()
        }

        suppose("some accounts get ERC20 tokens") {
            contract.transferAndMine(accounts[1].address, BigInteger("100"))
            contract.transferAndMine(accounts[2].address, BigInteger("200"))
            contract.transferAndMine(accounts[3].address, BigInteger("300"))
            contract.transferAndMine(accounts[4].address, BigInteger("400"))
        }

        val startBlock = null
        val endBlock1 = hardhatContainer.blockNumber()

        contract.applyWeb3jFilterFix(startBlock, endBlock1)

        suppose("some additional transactions of ERC20 token are made") {
            contract.transferAndMine(accounts[1].address, BigInteger("900"))
            contract.transferAndMine(accounts[5].address, BigInteger("1000"))
            contract.transferAndMine(accounts[6].address, BigInteger("2000"))
        }

        verify("correct balances are fetched for first end block") {
            val service = Web3jBlockchainService(hardhatProperties())
            val balances = service.fetchErc20AccountBalances(
                chainId = Chain.HARDHAT_TESTNET.id,
                erc20ContractAddress = ContractAddress(contract.contractAddress),
                ignoredErc20Addresses = emptySet(),
                startBlock = startBlock,
                endBlock = endBlock1
            )

            assertThat(balances).withMessage().containsExactlyInAnyOrder(
                AccountBalance(WalletAddress(mainAccount.address), Balance(BigInteger("9000"))),
                AccountBalance(WalletAddress(accounts[1].address), Balance(BigInteger("100"))),
                AccountBalance(WalletAddress(accounts[2].address), Balance(BigInteger("200"))),
                AccountBalance(WalletAddress(accounts[3].address), Balance(BigInteger("300"))),
                AccountBalance(WalletAddress(accounts[4].address), Balance(BigInteger("400")))
            )
        }

        val endBlock2 = hardhatContainer.blockNumber()

        verify("correct balances are fetched for second end block") {
            val service = Web3jBlockchainService(hardhatProperties())
            val balances = service.fetchErc20AccountBalances(
                chainId = Chain.HARDHAT_TESTNET.id,
                erc20ContractAddress = ContractAddress(contract.contractAddress),
                ignoredErc20Addresses = emptySet(),
                startBlock = startBlock,
                endBlock = endBlock2
            )

            assertThat(balances).withMessage().containsExactlyInAnyOrder(
                AccountBalance(WalletAddress(mainAccount.address), Balance(BigInteger("5100"))),
                AccountBalance(WalletAddress(accounts[1].address), Balance(BigInteger("1000"))),
                AccountBalance(WalletAddress(accounts[2].address), Balance(BigInteger("200"))),
                AccountBalance(WalletAddress(accounts[3].address), Balance(BigInteger("300"))),
                AccountBalance(WalletAddress(accounts[4].address), Balance(BigInteger("400"))),
                AccountBalance(WalletAddress(accounts[5].address), Balance(BigInteger("1000"))),
                AccountBalance(WalletAddress(accounts[6].address), Balance(BigInteger("2000")))
            )
        }
    }

    @Test
    fun mustThrowExceptionForNonExistentContract() {
        verify("exception is thrown for non-existent contract") {
            assertThrows<InternalException>(message) {
                val service = Web3jBlockchainService(hardhatProperties())
                service.fetchErc20AccountBalances(
                    chainId = Chain.HARDHAT_TESTNET.id,
                    erc20ContractAddress = ContractAddress(accounts[0].address),
                    ignoredErc20Addresses = emptySet(),
                    startBlock = null,
                    endBlock = BlockNumber(BigInteger.TEN)
                )
            }
        }
    }

    @Test
    fun mustCorrectlyFetchAssetOwner() {
        val mainAccount = accounts[0]

        val contract = suppose("simple ERC20 contract is deployed") {
            SimpleERC20.deploy(
                hardhatContainer.web3j,
                mainAccount,
                DefaultGasProvider(),
                listOf(mainAccount.address),
                listOf(BigInteger("10000")),
                mainAccount.address
            ).sendAndMine()
        }

        verify("correct asset owner is fetched") {
            val service = Web3jBlockchainService(hardhatProperties())
            val assetOwner = service.getAssetOwner(
                chainId = Chain.HARDHAT_TESTNET.id,
                assetAddress = ContractAddress(contract.contractAddress)
            )

            assertThat(assetOwner).withMessage()
                .isEqualTo(WalletAddress(mainAccount.address))
        }
    }

    @Test
    fun mustThrowExceptionWhenFetchingAssetOwnerFails() {
        val mainAccount = accounts[0]

        verify("exception is thrown when fetching asset owner") {
            val service = Web3jBlockchainService(hardhatProperties())

            assertThrows<InternalException>(message) {
                service.getAssetOwner(
                    chainId = Chain.HARDHAT_TESTNET.id,
                    assetAddress = ContractAddress(mainAccount.address)
                )
            }
        }
    }

    @Test
    fun mustCorrectlyFetchPayoutsForAdmin() {
        val mainAccount = accounts[0]
        val hash = HashFunction.KECCAK_256.invoke("test")
        val owner1 = WalletAddress("aaa1")
        val owner2 = WalletAddress("aaa2")
        val owner3 = WalletAddress("aaa3")
        val payouts = listOf(
            createPayout(id = 0, owner = owner1, asset = "a", hash = hash),
            createPayout(id = 1, owner = owner1, asset = "a", hash = hash),
            createPayout(id = 2, owner = owner2, asset = "b", hash = hash),
            createPayout(id = 3, owner = owner2, asset = "c", hash = hash),
            createPayout(id = 4, owner = owner3, asset = "d", hash = hash)
        )

        val manager = suppose("simple payout manager contract is deployed") {
            SimplePayoutManager.deploy(
                hardhatContainer.web3j,
                mainAccount,
                DefaultGasProvider(),
                payouts
            ).sendAndMine()
        }

        val service = suppose("simple payout service contract is deployed") {
            SimplePayoutService.deploy(
                hardhatContainer.web3j,
                mainAccount,
                DefaultGasProvider()
            ).sendAndMine()
        }

        val issuer1 = ContractAddress("1a")
        val issuer2 = ContractAddress("1b")
        val issuer1Payouts = payouts.subList(0, 3)
        val issuer2Payouts = payouts.drop(3)

        suppose("issuers have some payouts") {
            service.addIssuerPayouts(issuer1.rawValue, issuer1Payouts.map { it.payoutId }).sendAndMine()
            service.addIssuerPayouts(issuer2.rawValue, issuer2Payouts.map { it.payoutId }).sendAndMine()
        }

        val nullParams = GetPayoutsForAdminParams(
            chainId = Chain.HARDHAT_TESTNET.id,
            issuer = null,
            assetFactories = emptyList(),
            payoutService = ContractAddress(service.contractAddress),
            payoutManager = ContractAddress(manager.contractAddress),
            owner = null
        )
        val blockchainService = Web3jBlockchainService(hardhatProperties())

        verify("all payouts are fetched when issuer and owner are both null") {
            assertThat(blockchainService.getPayoutsForAdmin(nullParams)).withMessage()
                .containsExactlyInAnyOrderElementsOf(payouts.map { Payout(it) })
        }

        verify("owner payouts are fetched when issuer is null") {
            assertThat(blockchainService.getPayoutsForAdmin(nullParams.copy(owner = owner1))).withMessage()
                .containsExactlyInAnyOrderElementsOf(payouts.map { Payout(it) }.filter { it.payoutOwner == owner1 })
            assertThat(blockchainService.getPayoutsForAdmin(nullParams.copy(owner = owner2))).withMessage()
                .containsExactlyInAnyOrderElementsOf(payouts.map { Payout(it) }.filter { it.payoutOwner == owner2 })
            assertThat(blockchainService.getPayoutsForAdmin(nullParams.copy(owner = owner3))).withMessage()
                .containsExactlyInAnyOrderElementsOf(payouts.map { Payout(it) }.filter { it.payoutOwner == owner3 })
        }

        verify("issuer payouts are fetched when owner is null") {
            assertThat(blockchainService.getPayoutsForAdmin(nullParams.copy(issuer = issuer1))).withMessage()
                .containsExactlyInAnyOrderElementsOf(issuer1Payouts.map { Payout(it) })
            assertThat(blockchainService.getPayoutsForAdmin(nullParams.copy(issuer = issuer2))).withMessage()
                .containsExactlyInAnyOrderElementsOf(issuer2Payouts.map { Payout(it) })
        }

        verify("owner payouts for specific issuer are fetched") {
            // issuer 1
            assertThat(blockchainService.getPayoutsForAdmin(nullParams.copy(issuer = issuer1, owner = owner1)))
                .withMessage()
                .containsExactlyInAnyOrderElementsOf(
                    issuer1Payouts.map { Payout(it) }.filter { it.payoutOwner == owner1 }
                )
            assertThat(blockchainService.getPayoutsForAdmin(nullParams.copy(issuer = issuer1, owner = owner2)))
                .withMessage()
                .containsExactlyInAnyOrderElementsOf(
                    issuer1Payouts.map { Payout(it) }.filter { it.payoutOwner == owner2 }
                )
            assertThat(blockchainService.getPayoutsForAdmin(nullParams.copy(issuer = issuer1, owner = owner3)))
                .withMessage()
                .containsExactlyInAnyOrderElementsOf(
                    issuer1Payouts.map { Payout(it) }.filter { it.payoutOwner == owner3 }
                )

            // issuer 2
            assertThat(blockchainService.getPayoutsForAdmin(nullParams.copy(issuer = issuer2, owner = owner1)))
                .withMessage()
                .containsExactlyInAnyOrderElementsOf(
                    issuer2Payouts.map { Payout(it) }.filter { it.payoutOwner == owner1 }
                )
            assertThat(blockchainService.getPayoutsForAdmin(nullParams.copy(issuer = issuer2, owner = owner2)))
                .withMessage()
                .containsExactlyInAnyOrderElementsOf(
                    issuer2Payouts.map { Payout(it) }.filter { it.payoutOwner == owner2 }
                )
            assertThat(blockchainService.getPayoutsForAdmin(nullParams.copy(issuer = issuer2, owner = owner3)))
                .withMessage()
                .containsExactlyInAnyOrderElementsOf(
                    issuer2Payouts.map { Payout(it) }.filter { it.payoutOwner == owner3 }
                )
        }
    }

    @Test
    fun mustThrowExceptionWhenFetchingPayoutsForAdminFails() {
        val nullParams = GetPayoutsForAdminParams(
            chainId = Chain.HARDHAT_TESTNET.id,
            issuer = null,
            assetFactories = emptyList(),
            payoutService = ContractAddress("0"),
            payoutManager = ContractAddress("0"),
            owner = null
        )
        val blockchainService = Web3jBlockchainService(hardhatProperties())

        verify("exception is thrown when issuer and owner are both null") {
            assertThrows<InternalException>(message) {
                blockchainService.getPayoutsForAdmin(nullParams)
            }
        }

        verify("exception is thrown when issuer is null") {
            assertThrows<InternalException>(message) {
                blockchainService.getPayoutsForAdmin(nullParams.copy(owner = WalletAddress("1")))
            }
        }

        verify("exception is thrown when owner is null") {
            assertThrows<InternalException>(message) {
                blockchainService.getPayoutsForAdmin(nullParams.copy(issuer = ContractAddress("1")))
            }
        }

        verify("exception is thrown") {
            assertThrows<InternalException>(message) {
                blockchainService.getPayoutsForAdmin(
                    nullParams.copy(issuer = ContractAddress("1"), owner = WalletAddress("2"))
                )
            }
        }
    }

    @Test
    fun mustCorrectlyFetchPayoutsForInvestor() {
        val mainAccount = accounts[0]
        val hash = HashFunction.KECCAK_256.invoke("test")
        val owner1 = WalletAddress("aaa1")
        val owner2 = WalletAddress("aaa2")
        val owner3 = WalletAddress("aaa3")
        val investor1 = WalletAddress("bbb1")
        val investor2 = WalletAddress("bbb2")
        val payoutsAndInvestments = listOf(
            createPayoutWithInvestor(id = 0, owner = owner1, asset = "a", hash = hash, investor = investor1),
            createPayoutWithInvestor(id = 1, owner = owner1, asset = "a", hash = hash, investor = investor1),
            createPayoutWithInvestor(id = 2, owner = owner2, asset = "b", hash = hash, investor = investor2),
            createPayoutWithInvestor(id = 3, owner = owner2, asset = "c", hash = hash, investor = investor2),
            createPayoutWithInvestor(id = 4, owner = owner3, asset = "d", hash = hash, investor = investor1)
        )

        val payouts = payoutsAndInvestments.map { it.first }

        val manager = suppose("simple payout manager contract is deployed") {
            SimplePayoutManager.deploy(
                hardhatContainer.web3j,
                mainAccount,
                DefaultGasProvider(),
                payouts
            ).sendAndMine()
        }

        val service = suppose("simple payout service contract is deployed") {
            SimplePayoutService.deploy(
                hardhatContainer.web3j,
                mainAccount,
                DefaultGasProvider()
            ).sendAndMine()
        }

        val issuer1 = ContractAddress("1a")
        val issuer2 = ContractAddress("1b")
        val issuer1PayoutsAndInvestments = payoutsAndInvestments.subList(0, 3)
        val issuer2PayoutsAndInvestments = payoutsAndInvestments.drop(3)
        val issuer1Payouts = issuer1PayoutsAndInvestments.map { it.first }
        val issuer2Payouts = issuer2PayoutsAndInvestments.map { it.first }

        suppose("issuers have some payouts") {
            service.addIssuerPayouts(issuer1.rawValue, issuer1Payouts.map { it.payoutId }).sendAndMine()
            service.addIssuerPayouts(issuer2.rawValue, issuer2Payouts.map { it.payoutId }).sendAndMine()
        }

        suppose("some investments are claimed") {
            payoutsAndInvestments.forEach {
                manager.setClaim(it.second.payoutId, it.second.investor, it.second.amountClaimed).sendAndMine()
            }
        }

        val investor1NullParams = GetPayoutsForInvestorParams(
            chainId = Chain.HARDHAT_TESTNET.id,
            issuer = null,
            assetFactories = emptyList(),
            payoutService = ContractAddress(service.contractAddress),
            payoutManager = ContractAddress(manager.contractAddress),
            investor = investor1
        )
        val investor2NullParams = investor1NullParams.copy(investor = investor2)
        val blockchainService = Web3jBlockchainService(hardhatProperties())

        verify("all payouts states are fetched when issuer is null") {
            // investor 1
            assertThat(blockchainService.getPayoutsForInvestor(investor1NullParams))
                .withMessage()
                .containsExactlyInAnyOrderElementsOf(payoutsAndInvestments.forInvestor(investor1))

            // investor 2
            assertThat(blockchainService.getPayoutsForInvestor(investor2NullParams))
                .withMessage()
                .containsExactlyInAnyOrderElementsOf(payoutsAndInvestments.forInvestor(investor2))
        }

        verify("all payouts states are") {
            // investor 1
            assertThat(blockchainService.getPayoutsForInvestor(investor1NullParams.copy(issuer = issuer1)))
                .withMessage()
                .containsExactlyInAnyOrderElementsOf(issuer1PayoutsAndInvestments.forInvestor(investor1))
            assertThat(blockchainService.getPayoutsForInvestor(investor1NullParams.copy(issuer = issuer2)))
                .withMessage()
                .containsExactlyInAnyOrderElementsOf(issuer2PayoutsAndInvestments.forInvestor(investor1))

            // investor 2
            assertThat(blockchainService.getPayoutsForInvestor(investor2NullParams.copy(issuer = issuer1)))
                .withMessage()
                .containsExactlyInAnyOrderElementsOf(issuer1PayoutsAndInvestments.forInvestor(investor2))
            assertThat(blockchainService.getPayoutsForInvestor(investor2NullParams.copy(issuer = issuer2)))
                .withMessage()
                .containsExactlyInAnyOrderElementsOf(issuer2PayoutsAndInvestments.forInvestor(investor2))
        }
    }

    @Test
    fun mustThrowExceptionWhenFetchingPayoutsForInvestorFails() {
        val nullParams = GetPayoutsForInvestorParams(
            chainId = Chain.HARDHAT_TESTNET.id,
            issuer = null,
            assetFactories = emptyList(),
            payoutService = ContractAddress("0"),
            payoutManager = ContractAddress("0"),
            investor = WalletAddress("1")
        )
        val blockchainService = Web3jBlockchainService(hardhatProperties())

        verify("exception is thrown when issuer is null") {
            assertThrows<InternalException>(message) {
                blockchainService.getPayoutsForInvestor(nullParams)
            }
        }

        verify("exception is thrown") {
            assertThrows<InternalException>(message) {
                blockchainService.getPayoutsForInvestor(nullParams.copy(issuer = ContractAddress("2")))
            }
        }
    }

    private fun SimpleERC20.transferAndMine(address: String, amount: BigInteger) {
        transfer(address, amount).sendAsync()
        hardhatContainer.mineUntil {
            balanceOf(address).send() == amount
        }
    }

    private fun <T> RemoteCall<T>.sendAndMine(): T {
        val future = sendAsync()
        hardhatContainer.waitAndMine()
        return future.get()
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

    private fun hardhatProperties() = ApplicationProperties().apply { infuraId = hardhatContainer.mappedPort }

    private fun createPayout(id: Long, owner: WalletAddress, asset: String, hash: Hash): PayoutStruct =
        PayoutStruct(
            BigInteger.valueOf(id),
            owner.rawValue,
            "payout-info-$id",
            false,
            ContractAddress(asset).rawValue,
            BigInteger.valueOf(id * 1_000L),
            emptyList(),
            Numeric.hexStringToByteArray(hash.value),
            BigInteger.valueOf(id),
            BigInteger.valueOf(id + 1),
            "ipfs-hash-$id",
            ContractAddress("ffff").rawValue,
            BigInteger.valueOf(id * 500L),
            BigInteger.valueOf(id * 500L)
        )

    private fun createPayoutWithInvestor(
        id: Long,
        owner: WalletAddress,
        asset: String,
        hash: Hash,
        investor: WalletAddress
    ): Pair<PayoutStruct, PayoutStateForInvestor> =
        Pair(
            createPayout(id = id, owner = owner, asset = asset, hash = hash),
            PayoutStateForInvestor(
                BigInteger.valueOf(id),
                investor.rawValue,
                BigInteger.valueOf(id * 31L)
            )
        )

    private fun List<Pair<PayoutStruct, PayoutStateForInvestor>>.forInvestor(
        investor: WalletAddress
    ): List<PayoutForInvestor> =
        map {
            val state = it.second

            if (WalletAddress(state.investor) == investor) {
                PayoutForInvestor(it.first, it.second)
            } else {
                PayoutForInvestor(it.first, PayoutStateForInvestor(state.payoutId, investor.rawValue, BigInteger.ZERO))
            }
        }
}
