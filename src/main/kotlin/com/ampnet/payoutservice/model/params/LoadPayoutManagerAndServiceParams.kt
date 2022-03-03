package com.ampnet.payoutservice.model.params

import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress

interface LoadPayoutManagerAndServiceParams {
    val chainId: ChainId
    val payoutService: ContractAddress
    val payoutManager: ContractAddress
}
