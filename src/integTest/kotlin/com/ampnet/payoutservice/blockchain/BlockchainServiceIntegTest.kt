package com.ampnet.payoutservice.blockchain

import com.ampnet.payoutservice.TestBase
import com.ampnet.payoutservice.blockchain.properties.Chain
import com.ampnet.payoutservice.blockchain.properties.ChainPropertiesHandler
import com.ampnet.payoutservice.config.ApplicationProperties
import com.ampnet.payoutservice.testcontainers.HardhatTestContainer
import org.junit.jupiter.api.Test
import org.web3j.tx.gas.DefaultGasProvider
import java.math.BigInteger

class BlockchainServiceIntegTest : TestBase() {

    @Test
    fun testDeploy() {
        val chainPropertiesHandler = ChainPropertiesHandler(ApplicationProperties().apply { infuraId = "" })
        val chainProperties = chainPropertiesHandler.getBlockchainProperties(Chain.HARDHAT_TESTNET_LOCALHOST.id)
        val credentials = HardhatTestContainer.accounts[0]
        Thread.sleep(20_000L) // TODO replace with hardhat cotnainer health check
        val contract = SimpleERC20.deploy(
            chainProperties.web3j,
            credentials,
            DefaultGasProvider(),
            listOf(credentials.address),
            listOf(BigInteger("1000"))
        ).send()
        val balance = contract.balanceOf(credentials.address).send()
        println("balance: $balance")
    }
}
