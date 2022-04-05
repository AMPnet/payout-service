package com.ampnet.payoutservice.model.result

import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.WalletAddress
import java.util.UUID

data class PendingSnapshot(
    val id: UUID,
    val name: String,
    val chainId: ChainId,
    val assetAddress: ContractAddress,
    val blockNumber: BlockNumber,
    val ignoredHolderAddresses: Set<WalletAddress>,
    val ownerAddress: WalletAddress
)
