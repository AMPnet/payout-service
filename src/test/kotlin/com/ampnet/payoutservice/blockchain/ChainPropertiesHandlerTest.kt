package com.ampnet.payoutservice.blockchain

import com.ampnet.payoutservice.TestBase
import com.ampnet.payoutservice.blockchain.properties.Chain
import com.ampnet.payoutservice.blockchain.properties.ChainPropertiesHandler
import com.ampnet.payoutservice.config.ApplicationProperties
import com.ampnet.payoutservice.exception.ErrorCode
import com.ampnet.payoutservice.exception.InternalException
import com.ampnet.payoutservice.util.ChainId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ChainPropertiesHandlerTest : TestBase() {

    @Test
    fun mustCorrectlyCreateChainPropertiesWithServices() {
        val chainPropertiesHandler = suppose("chain properties handler is created from application properties") {
            ChainPropertiesHandler(ApplicationProperties().apply { infuraId = "" })
        }

        verify("chain properties with services are correctly created") {
            val chainProperties = chainPropertiesHandler.getBlockchainProperties(Chain.MATIC_TESTNET_MUMBAI.id)
            assertThat(chainProperties.web3j).withMessage().isNotNull()
        }
    }

    @Test
    fun mustThrowExceptionForInvalidChainId() {
        val chainPropertiesHandler = suppose("chain properties handler is created from application properties") {
            ChainPropertiesHandler(ApplicationProperties())
        }

        verify("InternalException is thrown") {
            val exception = assertThrows<InternalException>(message) {
                chainPropertiesHandler.getBlockchainProperties(ChainId(-1))
            }
            assertThat(exception.errorCode).withMessage().isEqualTo(ErrorCode.BLOCKCHAIN_ID)
        }
    }

    @Test
    fun mustReturnDefaultRpcIfInfuraIdIsMissing() {
        val chainPropertiesHandler = suppose("chain properties handler is created from application properties") {
            val applicationProperties = ApplicationProperties().apply { infuraId = "" }
            ChainPropertiesHandler(applicationProperties)
        }

        verify("correct RPC URL is returned") {
            val chain = Chain.MATIC_TESTNET_MUMBAI
            val rpc = chainPropertiesHandler.getChainRpcUrl(chain)
            assertThat(rpc).withMessage().isEqualTo(chain.rpcUrl)
        }
    }

    @Test
    fun mustReturnDefaultRpcWhenChainDoesNotHaveInfuraRpcDefined() {
        val chainPropertiesHandler = suppose("chain properties handler is created from application properties") {
            val applicationProperties = ApplicationProperties().apply { infuraId = "" }
            ChainPropertiesHandler(applicationProperties)
        }

        verify("correct RPC URL is returned") {
            val chain = Chain.HARDHAT_TESTNET
            val rpc = chainPropertiesHandler.getChainRpcUrl(chain)
            assertThat(rpc).withMessage().isEqualTo(chain.rpcUrl)
        }
    }

    @Test
    fun mustReturnInfuraRpc() {
        val infuraId = "some-id"

        val chainPropertiesHandler = suppose("chain properties handler is created from application properties") {
            val applicationProperties = ApplicationProperties().apply { this.infuraId = infuraId }
            ChainPropertiesHandler(applicationProperties)
        }

        verify("correct Infura RPC URL is returned") {
            val chain = Chain.MATIC_TESTNET_MUMBAI
            val rpc = chainPropertiesHandler.getChainRpcUrl(chain)
            assertThat(rpc).withMessage().isEqualTo(chain.infura + infuraId)
        }
    }
}
