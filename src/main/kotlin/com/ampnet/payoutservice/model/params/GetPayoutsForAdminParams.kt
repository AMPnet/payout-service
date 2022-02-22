package com.ampnet.payoutservice.model.params

import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.WalletAddress

data class GetPayoutsForAdminParams(
    override val chainId: ChainId,
    override val issuer: ContractAddress?,
    override val assetFactories: List<ContractAddress>,
    override val payoutService: ContractAddress,
    override val payoutManager: ContractAddress,
    val owner: WalletAddress?
) : LoadPayoutManagerAndServiceParams, GetIssuerPayoutsParams
