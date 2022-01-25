package com.ampnet.payoutservice.testcontainers

import com.ampnet.payoutservice.util.BlockNumber
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy
import org.web3j.crypto.Credentials
import org.web3j.protocol.Web3j
import org.web3j.protocol.Web3jService
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.methods.response.VoidResponse
import org.web3j.protocol.http.HttpService
import java.time.Duration
import java.time.temporal.ChronoUnit

class HardhatTestContainer : GenericContainer<HardhatTestContainer>("gluwa/hardhat-dev:1.0.0") {

    companion object {
        private const val hardhatPort = 8545
    }

    private val web3jService: Web3jService
    val web3j: Web3j
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
    val mappedPort: String

    init {
        waitStrategy = LogMessageWaitStrategy()
            .withRegEx("Started HTTP and WebSocket JSON-RPC server at .*")
            .withTimes(1)
            .withStartupTimeout(Duration.of(60, ChronoUnit.SECONDS))

        addExposedPort(hardhatPort)
        start()

        mappedPort = getMappedPort(hardhatPort).toString()

        System.setProperty("HARDHAT_PORT", mappedPort)

        web3jService = HttpService("http://localhost:$mappedPort")
        web3j = Web3j.build(web3jService)
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
