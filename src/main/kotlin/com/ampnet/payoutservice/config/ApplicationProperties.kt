package com.ampnet.payoutservice.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "payoutservice")
class ApplicationProperties {
    val ipfs = IpfsProperties()
    var infuraId: String = ""
}

class IpfsProperties {
    var url = "https://api.pinata.cloud/"
    var apiKey = ""
    var secretApiKey = ""
}
