package com.ampnet.payoutservice.model.params

import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.WalletAddress

data class CreateSnapshotParams(
    val name: String,
    val chainId: ChainId,
    val assetAddress: ContractAddress,
    val ownerAddress: WalletAddress,
    val payoutBlock: BlockNumber,
    val ignoredHolderAddresses: Set<WalletAddress>
)
