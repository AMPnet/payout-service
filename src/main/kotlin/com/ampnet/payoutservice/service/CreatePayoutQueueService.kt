package com.ampnet.payoutservice.service

import com.ampnet.payoutservice.controller.response.CreatePayoutTaskResponse
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.WalletAddress
import java.util.UUID

interface CreatePayoutQueueService { // TODO test

    fun submitTask(
        chainId: ChainId,
        assetAddress: ContractAddress,
        requesterAddress: WalletAddress,
        issuerAddress: ContractAddress?,
        payoutBlock: BlockNumber,
        ignoredAssetAddresses: Set<WalletAddress>
    ): UUID

    fun getTaskById(taskId: UUID): CreatePayoutTaskResponse?
}
