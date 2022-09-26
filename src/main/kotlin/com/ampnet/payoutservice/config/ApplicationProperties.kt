package com.ampnet.payoutservice.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration
import java.math.BigInteger

@Configuration
@ConfigurationProperties(prefix = "payoutservice")
class ApplicationProperties {
    val jwt = JwtProperties()
    val ipfs = IpfsProperties()
    val payout = PayoutProperties()
    val createPayoutQueue = QueueProperties()
    val chainEthereum = ChainProperties()
    val chainGoerli = ChainProperties()
    val chainMatic = ChainProperties()
    val chainMumbai = ChainProperties()
    val chainHardhatTestnet = ChainProperties()
    val chainBsc = ChainProperties()
    val chainXdai = ChainProperties()
    val chainFantom = ChainProperties()
    val chainMoonriver = ChainProperties()
    val chainAvalanche = ChainProperties()
    val chainAurora = ChainProperties()
    var infuraId: String = ""
}

class JwtProperties {
    lateinit var publicKey: String
}

class IpfsProperties {
    var url = "https://api.pinata.cloud/"
    var apiKey = ""
    var secretApiKey = ""
}

class PayoutProperties {
    var checkAssetOwner = true
}

class ChainProperties {
    var startBlockNumber: BigInteger? = null
    var rpcUrlOverride: String = ""
}

@Suppress("MagicNumber")
class QueueProperties {
    var polling: Long = 5_000L
    var initialDelay: Long = 15_000L
}
