package com.ampnet.payoutservice.service

import com.ampnet.payoutservice.TestBase
import com.ampnet.payoutservice.config.ApplicationProperties
import com.ampnet.payoutservice.config.RestTemplateConfig
import com.ampnet.payoutservice.exception.InternalException
import com.ampnet.payoutservice.util.IpfsHash
import com.ampnet.payoutservice.wiremock.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.equalTo
import com.github.tomakehurst.wiremock.client.WireMock.equalToJson
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType

@RestClientTest
@Import(PinataIpfsService::class, ApplicationProperties::class, RestTemplateConfig::class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PinataIpfsServiceIntegTest : TestBase() {

    @Autowired
    private lateinit var service: IpfsService

    @BeforeEach
    fun beforeEach() {
        WireMock.start()
    }

    @AfterEach
    fun afterEach() {
        WireMock.stop()
    }

    @Test
    fun mustCorrectlyUploadJsonToIpfs() {
        val requestJson = "{\"test\":1}"
        val ipfsHash = IpfsHash("test-hash")
        val responseJson =
            """
            {
                "IpfsHash": "${ipfsHash.value}",
                "PinSize": 1,
                "Timestamp": "2022-01-01T00:00:00Z"
            }
            """.trimIndent()

        suppose("IPFS JSON upload will succeed") {
            WireMock.server.stubFor(
                post(urlPathEqualTo("/pinning/pinJSONToIPFS"))
                    .withHeader("pinata_api_key", equalTo("test-api-key"))
                    .withHeader("pinata_secret_api_key", equalTo("test-api-secret"))
                    .withRequestBody(equalToJson(requestJson))
                    .willReturn(
                        aResponse()
                            .withBody(responseJson)
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withStatus(200)
                    )
            )
        }

        verify("correct IPFS hash is returned for JSON upload") {
            val result = service.pinJsonToIpfs(mapOf("test" to 1))
            assertThat(result).withMessage()
                .isEqualTo(ipfsHash)
        }
    }

    @Test
    fun mustThrowExceptionWhenIpfsHashIsMissingInResponse() {
        val requestJson = "{\"test\":1}"
        val responseJson =
            """
            {
                "PinSize": 1,
                "Timestamp": "2022-01-01T00:00:00Z"
            }
            """.trimIndent()

        suppose("IPFS JSON upload will succeed without IPFS hash in response") {
            WireMock.server.stubFor(
                post(urlPathEqualTo("/pinning/pinJSONToIPFS"))
                    .withHeader("pinata_api_key", equalTo("test-api-key"))
                    .withHeader("pinata_secret_api_key", equalTo("test-api-secret"))
                    .withRequestBody(equalToJson(requestJson))
                    .willReturn(
                        aResponse()
                            .withBody(responseJson)
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withStatus(200)
                    )
            )
        }

        verify("exception is thrown when IPFS hash is missing in response") {
            assertThrows<InternalException>(message) {
                service.pinJsonToIpfs(mapOf("test" to 1))
            }
        }
    }

    @Test
    fun mustThrowExceptionForNon2xxResponseCode() {
        val requestJson = "{\"test\":1}"

        suppose("IPFS JSON upload will succeed without IPFS hash in response") {
            WireMock.server.stubFor(
                post(urlPathEqualTo("/pinning/pinJSONToIPFS"))
                    .withHeader("pinata_api_key", equalTo("test-api-key"))
                    .withHeader("pinata_secret_api_key", equalTo("test-api-secret"))
                    .withRequestBody(equalToJson(requestJson))
                    .willReturn(
                        aResponse()
                            .withBody("{}")
                            .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .withStatus(400)
                    )
            )
        }

        verify("exception is thrown when non 2xx response is returned") {
            assertThrows<InternalException>(message) {
                service.pinJsonToIpfs(mapOf("test" to 1))
            }
        }
    }
}
