package com.ampnet.payoutservice.model.params

import com.ampnet.payoutservice.util.ContractAddress

interface GetIssuerPayoutsParams {
    val issuer: ContractAddress?
    val assetFactories: List<ContractAddress>
    val payoutManager: ContractAddress
}
