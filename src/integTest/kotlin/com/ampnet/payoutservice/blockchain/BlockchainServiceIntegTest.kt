package com.ampnet.payoutservice.blockchain

import com.ampnet.payoutservice.TestBase
import com.ampnet.payoutservice.blockchain.properties.Chain
import com.ampnet.payoutservice.config.ApplicationProperties
import com.ampnet.payoutservice.exception.InternalException
import com.ampnet.payoutservice.testcontainers.HardhatTestContainer
import com.ampnet.payoutservice.util.AccountBalance
import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.WalletAddress
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.methods.request.EthFilter
import org.web3j.tx.gas.DefaultGasProvider
import java.math.BigInteger

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BlockchainServiceIntegTest : TestBase() {

    private val hardhatContainer = HardhatTestContainer()
    private val accounts = hardhatContainer.accounts

    @Test
    fun mustCorrectlyFetchBalancesBasedOnBlockRange() {
        val mainAccount = accounts[0]

        val contract = suppose("Simple ERC20 contract is deployed") {
            val future = SimpleERC20.deploy(
                hardhatContainer.web3j,
                mainAccount,
                DefaultGasProvider(),
                listOf(mainAccount.address),
                listOf(BigInteger("10000"))
            ).sendAsync()
            hardhatContainer.waitAndMine()
            future.get()
        }

        suppose("Some accounts get ERC20 tokens") {
            contract.transferAndMine(accounts[1].address, BigInteger("100"))
            contract.transferAndMine(accounts[2].address, BigInteger("200"))
            contract.transferAndMine(accounts[3].address, BigInteger("300"))
            contract.transferAndMine(accounts[4].address, BigInteger("400"))
        }

        val startBlock = BlockNumber(BigInteger.ZERO)
        val endBlock1 = hardhatContainer.blockNumber()

        contract.applyWeb3jFilterFix(startBlock, endBlock1)

        suppose("Some additional transactions of ERC20 token are made") {
            contract.transferAndMine(accounts[1].address, BigInteger("900"))
            contract.transferAndMine(accounts[5].address, BigInteger("1000"))
            contract.transferAndMine(accounts[6].address, BigInteger("2000"))
        }

        verify("Correct balances are fetched for first end block") {
            val service = BlockchainServiceImpl(ApplicationProperties().apply { infuraId = "" })
            val balances = service.fetchErc20AccountBalances(
                chainId = Chain.HARDHAT_TESTNET_LOCALHOST.id,
                erc20ContractAddress = ContractAddress(contract.contractAddress),
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

        verify("Correct balances are fetched for second end block") {
            val service = BlockchainServiceImpl(ApplicationProperties().apply { infuraId = "" })
            val balances = service.fetchErc20AccountBalances(
                chainId = Chain.HARDHAT_TESTNET_LOCALHOST.id,
                erc20ContractAddress = ContractAddress(contract.contractAddress),
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
    fun mustCorrectlyFetchBalancesForNullStartBlock() {
        val mainAccount = accounts[0]

        val contract = suppose("Simple ERC20 contract is deployed") {
            val future = SimpleERC20.deploy(
                hardhatContainer.web3j,
                mainAccount,
                DefaultGasProvider(),
                listOf(mainAccount.address),
                listOf(BigInteger("10000"))
            ).sendAsync()
            hardhatContainer.waitAndMine()
            future.get()
        }

        suppose("Some accounts get ERC20 tokens") {
            contract.transferAndMine(accounts[1].address, BigInteger("100"))
            contract.transferAndMine(accounts[2].address, BigInteger("200"))
            contract.transferAndMine(accounts[3].address, BigInteger("300"))
            contract.transferAndMine(accounts[4].address, BigInteger("400"))
        }

        val startBlock = null
        val endBlock1 = hardhatContainer.blockNumber()

        contract.applyWeb3jFilterFix(startBlock, endBlock1)

        suppose("Some additional transactions of ERC20 token are made") {
            contract.transferAndMine(accounts[1].address, BigInteger("900"))
            contract.transferAndMine(accounts[5].address, BigInteger("1000"))
            contract.transferAndMine(accounts[6].address, BigInteger("2000"))
        }

        verify("Correct balances are fetched for first end block") {
            val service = BlockchainServiceImpl(ApplicationProperties().apply { infuraId = "" })
            val balances = service.fetchErc20AccountBalances(
                chainId = Chain.HARDHAT_TESTNET_LOCALHOST.id,
                erc20ContractAddress = ContractAddress(contract.contractAddress),
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

        verify("Correct balances are fetched for second end block") {
            val service = BlockchainServiceImpl(ApplicationProperties().apply { infuraId = "" })
            val balances = service.fetchErc20AccountBalances(
                chainId = Chain.HARDHAT_TESTNET_LOCALHOST.id,
                erc20ContractAddress = ContractAddress(contract.contractAddress),
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
        verify("Exception is thrown for non-existent contract") {
            assertThrows<InternalException>(message) {
                val service = BlockchainServiceImpl(ApplicationProperties().apply { infuraId = "" })
                service.fetchErc20AccountBalances(
                    chainId = Chain.HARDHAT_TESTNET_LOCALHOST.id,
                    erc20ContractAddress = ContractAddress(accounts[0].address),
                    startBlock = null,
                    endBlock = BlockNumber(BigInteger.TEN)
                )
            }
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
