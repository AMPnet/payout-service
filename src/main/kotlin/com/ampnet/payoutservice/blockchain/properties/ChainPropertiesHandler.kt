package com.ampnet.payoutservice.blockchain.properties

import com.ampnet.payoutservice.config.ApplicationProperties
import com.ampnet.payoutservice.exception.ErrorCode
import com.ampnet.payoutservice.exception.InternalException
import com.ampnet.payoutservice.util.ChainId
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService

class ChainPropertiesHandler(private val applicationProperties: ApplicationProperties) {

    private val blockchainPropertiesMap = mutableMapOf<ChainId, ChainPropertiesWithServices>()

    fun getBlockchainProperties(chainId: ChainId): ChainPropertiesWithServices {
        blockchainPropertiesMap[chainId]?.let { return it }
        val chain = getChain(chainId)
        val properties = generateBlockchainProperties(chain)
        blockchainPropertiesMap[chainId] = properties
        return properties
    }

    internal fun getChainRpcUrl(chain: Chain): String =
        if (chain.infura == null || applicationProperties.infuraId.isBlank()) {
            chain.rpcUrl
        } else {
            "${chain.infura}${applicationProperties.infuraId}"
        }

    private fun generateBlockchainProperties(chain: Chain): ChainPropertiesWithServices {
        val rpcUrl = getChainRpcUrl(chain)
        return ChainPropertiesWithServices(
            web3j = Web3j.build(HttpService(rpcUrl))
        )
    }

    private fun getChain(chainId: ChainId) = Chain.fromId(chainId)
        ?: throw InternalException(ErrorCode.BLOCKCHAIN_ID, "Blockchain id: $chainId not supported")
}
