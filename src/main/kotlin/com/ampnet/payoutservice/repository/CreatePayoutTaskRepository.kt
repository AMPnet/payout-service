package com.ampnet.payoutservice.repository

import com.ampnet.payoutservice.model.PendingCreatePayoutTask
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.IpfsHash
import com.ampnet.payoutservice.util.WalletAddress
import java.util.UUID

interface CreatePayoutTaskRepository {

    fun createPayoutTask(
        chainId: ChainId,
        assetAddress: ContractAddress,
        requesterAddress: WalletAddress,
        issuerAddress: ContractAddress?,
        payoutBlock: BlockNumber,
        ignoredAssetAddresses: Set<WalletAddress>
    ): UUID

    fun getPending(): PendingCreatePayoutTask?

    fun completeTask(taskId: UUID, merkleTreeRootId: UUID, merkleTreeIpfsHash: IpfsHash)

    fun failTask(taskId: UUID)
}
