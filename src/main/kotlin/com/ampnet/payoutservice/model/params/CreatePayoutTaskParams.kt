package com.ampnet.payoutservice.model.params

import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.WalletAddress

data class CreatePayoutTaskParams(
    val chainId: ChainId,
    val assetAddress: ContractAddress,
    val requesterAddress: WalletAddress,
    val issuerAddress: ContractAddress?,
    val payoutBlock: BlockNumber,
    val ignoredAssetAddresses: Set<WalletAddress>
)
