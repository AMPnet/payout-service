package com.ampnet.payoutservice.service

import com.ampnet.payoutservice.controller.response.CreatePayoutResponse
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.WalletAddress

interface PayoutService {
    fun createPayout(
        chainId: ChainId,
        assetAddress: ContractAddress,
        requesterAddress: WalletAddress,
        payoutBlock: BlockNumber,
        ignoredAssetAddresses: Set<WalletAddress>
    ): CreatePayoutResponse
}
