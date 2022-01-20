package com.ampnet.payoutservice.testcontainers

import com.ampnet.payoutservice.blockchain.properties.Chain
import com.ampnet.payoutservice.util.BlockNumber
import org.testcontainers.containers.GenericContainer
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.methods.response.VoidResponse
import org.web3j.protocol.http.HttpService
import java.io.IOException

object HardhatTestContainer : GenericContainer<HardhatTestContainer>("gluwa/hardhat-dev:1.0.0") {

    private val web3jService = HttpService(Chain.HARDHAT_TESTNET_LOCALHOST.rpcUrl)
    val web3j = Web3j.build(web3jService)
    val accounts: List<Credentials> = listOf(
        Credentials.create("0xabf82ff96b463e9d82b83cb9bb450fe87e6166d4db6d7021d0c71d7e960d5abe"),
        Credentials.create("0xdcb7118c9946a39cd40b661e0d368e4afcc3cc48d21aa750d8164ca2e44961c4"),
        Credentials.create("0x2d7aaa9b78d759813448eb26483284cd5e4344a17dede2ab7f062f0757113a28"),
        Credentials.create("0x0e5c6904f09186a0cfe945da201e9d9f0443e07d9e795a9d26cc5cbb882874ac"),
        Credentials.create("0x7f60d75be8f8833a47311c001adbc3771784c52ea9115200a516e3f050c3bc2b"),
        Credentials.create("0x949dbd0607598c41478b32c27da65ab550d54246922fa8978a8c1b9e901e06a6"),
        Credentials.create("0x87a3c9405478581d513a16075038e5869d02311371b757f7163200a09f230f18"),
        Credentials.create("0xe5faea48461ef5a0b78839573073e5a2f579155bf7a4cceb15e49b41963af6a3"),
        Credentials.create("0xccfb970ed6f3bb68a15d87a67071da16544c918cf978dc41906e686326bb953d"),
        Credentials.create("0x27a3706e23375353aabc8da00d59db6795abae3036dee967103088c8f15e5335")
    )

    init {
        addFixedExposedPort(8545, 8545)
        start()
        waitForHardhatStartup()
    }

    private fun waitForHardhatStartup() {
        val log = logger()
        log.info("Waiting for Hardhat service to start up...")

        var success = false
        var attempt = 0
        val maxAttempts = 10

        while (attempt < maxAttempts && success.not()) {
            attempt += 1

            try {
                web3j.ethBlockNumber().send()
                success = true
                log.info("Hardhat service is up and running")
            } catch (e: IOException) {
                log.info("Hardhat service is not yet up, waiting... [$attempt/$maxAttempts]")
                Thread.sleep(2500L)
            }
        }
    }

    private fun mine() {
        Request("evm_mine", emptyList<String>(), web3jService, VoidResponse::class.java).send()
    }

    fun waitAndMine() {
        Thread.sleep(2500L)
        mine()
    }

    fun mineUntil(maxAttempts: Int = 10, condition: () -> Boolean) {
        var attempt = 0
        while (attempt < maxAttempts && condition().not()) {
            attempt += 1
            mine()
        }
    }

    fun blockNumber(): BlockNumber = BlockNumber(web3j.ethBlockNumber().send().blockNumber)
}
