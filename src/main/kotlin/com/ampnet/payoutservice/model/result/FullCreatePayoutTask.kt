package com.ampnet.payoutservice.model.result

import com.ampnet.payoutservice.controller.response.PayoutResponse
import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.Hash
import com.ampnet.payoutservice.util.HashFunction
import com.ampnet.payoutservice.util.IpfsHash
import com.ampnet.payoutservice.util.PayoutStatus
import com.ampnet.payoutservice.util.SnapshotStatus
import com.ampnet.payoutservice.util.WalletAddress
import java.util.UUID

// TODO update in SD-708
data class FullCreatePayoutTask(
    val taskId: UUID,
    val chainId: ChainId,
    val assetAddress: ContractAddress,
    val payoutBlockNumber: BlockNumber,
    val ignoredAssetAddresses: Set<WalletAddress>,
    val requesterAddress: WalletAddress,
    val issuerAddress: ContractAddress?,
    val snapshotStatus: SnapshotStatus,
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

    fun toPayoutResponse(): PayoutResponse =
        PayoutResponse(
            taskId = taskId,
            status = PayoutStatus.fromTaskStatus(snapshotStatus),
            issuer = issuerAddress?.rawValue,

            payoutId = null,
            payoutOwner = requesterAddress.rawValue,
            payoutInfo = null,
            isCanceled = null,

            asset = assetAddress.rawValue,
            totalAssetAmount = data?.totalAssetAmount?.rawValue,
            ignoredHolderAddresses = ignoredAssetAddresses.mapTo(HashSet()) { it.rawValue },

            assetSnapshotMerkleRoot = data?.merkleRootHash?.value,
            assetSnapshotMerkleDepth = data?.merkleTreeDepth,
            assetSnapshotBlockNumber = payoutBlockNumber.value,
            assetSnapshotMerkleIpfsHash = data?.merkleTreeIpfsHash?.value,

            rewardAsset = null,
            totalRewardAmount = null,
            remainingRewardAmount = null
        )
}

data class FullCreatePayoutData(
    val totalAssetAmount: Balance,
    val merkleRootHash: Hash,
    val merkleTreeIpfsHash: IpfsHash,
    val merkleTreeDepth: Int,
    val hashFn: HashFunction
)
