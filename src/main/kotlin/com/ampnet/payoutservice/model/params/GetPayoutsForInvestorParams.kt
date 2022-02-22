package com.ampnet.payoutservice.model.params

import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.WalletAddress

data class GetPayoutsForInvestorParams(
    override val chainId: ChainId,
    override val issuer: ContractAddress?,
    override val assetFactories: List<ContractAddress>,
    override val payoutService: ContractAddress,
    override val payoutManager: ContractAddress,
    val investor: WalletAddress
) : LoadPayoutManagerAndServiceParams, GetIssuerPayoutsParams
