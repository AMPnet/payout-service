package com.ampnet.payoutservice.model.result

import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.Hash
import com.ampnet.payoutservice.util.HashFunction
import com.ampnet.payoutservice.util.IpfsHash
import com.ampnet.payoutservice.util.TaskStatus
import com.ampnet.payoutservice.util.WalletAddress
import java.util.UUID

data class FullCreatePayoutTask(
    val taskId: UUID,
    val chainId: ChainId,
    val assetAddress: ContractAddress,
    val payoutBlockNumber: BlockNumber,
    val ignoredAssetAddresses: Set<WalletAddress>,
    val requesterAddress: WalletAddress,
    val issuerAddress: ContractAddress?,
    val taskStatus: TaskStatus,
    val data: FullCreatePayoutData?
) {
    fun toKey(): PayoutKey? =
        data?.let {
            PayoutKey(
                asset = assetAddress,
                payoutBlockNumber = payoutBlockNumber,
                owner = requesterAddress,
                merkleRootHash = data.merkleRootHash,
                totalAssetAmount = data.totalAssetAmount
            )
        }
}

data class FullCreatePayoutData(
    val totalAssetAmount: Balance,
    val merkleRootHash: Hash,
    val merkleTreeIpfsHash: IpfsHash,
    val merkleTreeDepth: Int,
    val hashFn: HashFunction
)
