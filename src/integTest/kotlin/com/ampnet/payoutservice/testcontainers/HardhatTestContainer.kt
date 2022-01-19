package com.ampnet.payoutservice.testcontainers

import org.testcontainers.containers.GenericContainer
import org.web3j.crypto.Credentials

object HardhatTestContainer : GenericContainer<HardhatTestContainer>("gluwa/hardhat-dev:1.0.0") {

    val accounts: List<Credentials> = listOf( // TODO add test accounts
        Credentials.create("0xabf82ff96b463e9d82b83cb9bb450fe87e6166d4db6d7021d0c71d7e960d5abe")
    )

    init {
        addFixedExposedPort(8545, 8545)
        start()
    }
}
