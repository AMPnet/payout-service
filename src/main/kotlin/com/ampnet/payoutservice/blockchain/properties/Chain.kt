package com.ampnet.payoutservice.blockchain.properties

import com.ampnet.payoutservice.util.ChainId

@Suppress("MagicNumber")
enum class Chain(val id: ChainId, val rpcUrl: String, val infura: String?, val priceFeed: String?) {
    MATIC_MAIN(
        ChainId(137),
        "https://rpc-mainnet.matic.network/",
        "https://polygon-mainnet.infura.io/v3/",
        "https://gasstation-mainnet.matic.network"
    ),
    MATIC_TESTNET_MUMBAI(
        ChainId(80001),
        "https://rpc-mumbai.matic.today/",
        "https://polygon-mumbai.infura.io/v3/",
        "https://gasstation-mumbai.matic.today"
    ),
    ETHEREUM_MAIN(
        ChainId(1),
        "https://cloudflare-eth.com/",
        "https://mainnet.infura.io/v3/",
        null
    ),
    GOERLI_TESTNET(
        ChainId(5),
        "https://goerli.prylabs.net/",
        "https://goerli.infura.io/v3/",
        null
    ),
    HARDHAT_TESTNET(
        ChainId(31337),
        "http://hardhat:8545",
        null,
        null
    );

    companion object {
        private val map = values().associateBy(Chain::id)
        fun fromId(id: ChainId): Chain? = map[id]
    }
}
