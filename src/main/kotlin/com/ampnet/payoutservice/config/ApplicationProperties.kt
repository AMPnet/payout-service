package com.ampnet.payoutservice.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "com.ampnet.payoutservice")
class ApplicationProperties {
    lateinit var infuraId: String
}
