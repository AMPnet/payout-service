package com.ampnet.payoutservice.model

import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.WalletAddress
import java.util.UUID

data class PendingCreatePayoutTask(
    val taskId: UUID,
    val chainId: ChainId,
    val assetAddress: ContractAddress,
    val blockNumber: BlockNumber,
    val ignoredAssetAddresses: Set<WalletAddress>,
    val requesterAddress: WalletAddress,
    val issuerAddress: ContractAddress?
)
