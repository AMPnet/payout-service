package com.ampnet.payoutservice.blockchain.properties

import com.ampnet.payoutservice.util.BlockNumber
import org.web3j.protocol.Web3j

data class ChainPropertiesWithServices(
    val startBlock: BlockNumber,
    val web3j: Web3j
)
