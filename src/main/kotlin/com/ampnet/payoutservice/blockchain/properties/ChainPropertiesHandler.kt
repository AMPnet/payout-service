package com.ampnet.payoutservice.blockchain.properties

import com.ampnet.payoutservice.config.ApplicationProperties
import com.ampnet.payoutservice.config.ChainProperties
import com.ampnet.payoutservice.exception.ErrorCode
import com.ampnet.payoutservice.exception.InternalException
import com.ampnet.payoutservice.util.ChainId
import org.web3j.protocol.Web3j
import org.web3j.protocol.http.HttpService

class ChainPropertiesHandler(private val applicationProperties: ApplicationProperties) {

    private val blockchainPropertiesMap = mutableMapOf<ChainId, ChainPropertiesWithServices>()

    @Throws(InternalException::class)
    fun getBlockchainProperties(chainId: ChainId): ChainPropertiesWithServices {
        return blockchainPropertiesMap.computeIfAbsent(chainId) {
            generateBlockchainProperties(getChain(it))
        }
    }

    fun getChainProperties(chainId: ChainId): ChainProperties? {
        return when (chainId) {
            Chain.MATIC_MAIN.id -> applicationProperties.chainMatic
            Chain.MATIC_TESTNET_MUMBAI.id -> applicationProperties.chainMumbai
            Chain.ETHEREUM_MAIN.id -> applicationProperties.chainEthereum
            Chain.GOERLI_TESTNET.id -> applicationProperties.chainGoerli
            Chain.HARDHAT_TESTNET.id -> applicationProperties.chainHardhatTestnet
            Chain.BSC.id -> applicationProperties.chainBsc
            Chain.XDAI.id -> applicationProperties.chainXdai
            Chain.FANTOM.id -> applicationProperties.chainFantom
            Chain.MOONRIVER.id -> applicationProperties.chainMoonriver
            Chain.AVAX.id -> applicationProperties.chainAvalanche
            Chain.AURORA.id -> applicationProperties.chainAurora
            else -> null
        }
    }

    internal fun getChainRpcUrl(chain: Chain): String {
        val chainProperties = getChainProperties(chain.id)

        return if (chainProperties?.rpcUrlOverride?.isNotBlank() == true) {
            chainProperties.rpcUrlOverride
        } else if (chain.infura == null || applicationProperties.infuraId.isBlank()) {
            chain.rpcUrl
        } else {
            "${chain.infura}${applicationProperties.infuraId}"
        }
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
