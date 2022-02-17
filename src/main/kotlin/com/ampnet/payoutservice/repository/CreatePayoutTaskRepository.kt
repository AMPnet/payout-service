package com.ampnet.payoutservice.repository

import com.ampnet.payoutservice.model.CreatePayoutTask
import com.ampnet.payoutservice.model.PendingCreatePayoutTask
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.IpfsHash
import com.ampnet.payoutservice.util.WalletAddress
import java.math.BigInteger
import java.util.UUID

interface CreatePayoutTaskRepository { // TODO test
    fun getById(taskId: UUID): CreatePayoutTask?

    fun createPayoutTask(
        chainId: ChainId,
        assetAddress: ContractAddress,
        requesterAddress: WalletAddress,
        issuerAddress: ContractAddress?,
        payoutBlock: BlockNumber,
        ignoredAssetAddresses: Set<WalletAddress>
    ): UUID

    fun getPending(): PendingCreatePayoutTask?
    fun completeTask(taskId: UUID, merkleTreeRootId: UUID, merkleTreeIpfsHash: IpfsHash, totalAssetAmount: BigInteger)
    fun failTask(taskId: UUID)
}
