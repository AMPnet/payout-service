package com.ampnet.payoutservice.blockchain.properties

import com.ampnet.payoutservice.util.ChainId

@Suppress("MagicNumber")
enum class Chain(val id: ChainId, val rpcUrl: String, val infura: String?) {
    MATIC_MAIN(
        ChainId(137),
        "https://rpc-mainnet.matic.network/",
        "https://polygon-mainnet.infura.io/v3/"
    ),
    MATIC_TESTNET_MUMBAI(
        ChainId(80001),
        "https://rpc-mumbai.matic.today/",
        "https://polygon-mumbai.infura.io/v3/"
    ),
    ETHEREUM_MAIN(
        ChainId(1),
        "https://cloudflare-eth.com/",
        "https://mainnet.infura.io/v3/"
    ),
    GOERLI_TESTNET(
        ChainId(5),
        "https://goerli.prylabs.net/",
        "https://goerli.infura.io/v3/"
    ),
    HARDHAT_TESTNET(
        ChainId(31337),
        "http://hardhat:8545",
        "http://localhost:" // used in tests to inject HARDHAT_PORT via infuraId
    ),
    BSC(
        ChainId(56),
        "https://bsc-dataseed.binance.org/",
        null
    ),
    XDAI(
        ChainId(100),
        "https://wider-flashy-telescope.xdai.quiknode.pro/3032827d979350ef930fbaf0b041ca1c5262f962/",
        null
    ),
    FANTOM(
        ChainId(250),
        "https://rpc.ftm.tools/",
        null
    ),
    MOONRIVER(
        ChainId(1285),
        "https://rpc.moonriver.moonbeam.network/",
        null
    ),
    AVAX(
        ChainId(43114),
        "https://api.avax.network/ext/bc/C/rpc",
        null
    ),
    AURORA(
        ChainId(1313161554),
        "https://mainnet.aurora.dev/",
        null
    ),
    ARBITRUM(
        ChainId(42161),
        "https://arb1.arbitrum.io/rpc",
        null
    ),
    OPTIMISM(
        ChainId(10),
        "https://mainnet.optimism.io",
        null
    ),
    CELO(
        ChainId(42220),
        "https://icy-morning-tree.celo-mainnet.quiknode.pro/8ed0d34e8954249bcd847e85e7a721cbf7a3f464/",
        null
    ),
    PARATIME(
        ChainId(42262),
        "https://emerald.oasis.dev",
        null
    ),
    MOONBEAM(
        ChainId(1284),
        "https://moonbeam.public.blastapi.io",
        null
    );

    companion object {
        private val map = values().associateBy(Chain::id)
        fun fromId(id: ChainId): Chain? = map[id]
    }
}
