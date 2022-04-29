package com.ampnet.payoutservice.model.result

import com.ampnet.payoutservice.controller.response.SnapshotResponse
import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.Hash
import com.ampnet.payoutservice.util.HashFunction
import com.ampnet.payoutservice.util.IpfsHash
import com.ampnet.payoutservice.util.SnapshotFailureCause
import com.ampnet.payoutservice.util.SnapshotStatus
import com.ampnet.payoutservice.util.WalletAddress
import java.util.UUID

data class FullSnapshot(
    val id: UUID,
    val name: String,
    val chainId: ChainId,
    val assetAddress: ContractAddress,
    val blockNumber: BlockNumber,
    val ignoredHolderAddresses: Set<WalletAddress>,
    val ownerAddress: WalletAddress,
    val snapshotStatus: SnapshotStatus,
    val snapshotFailureCause: SnapshotFailureCause?,
    val data: FullSnapshotData?
) {
    fun toSnapshotResponse(): SnapshotResponse =
        SnapshotResponse(
            id = id,
            name = name,
            chainId = chainId.value,
            status = snapshotStatus,
            failureCause = snapshotFailureCause,
            owner = ownerAddress.rawValue,
            asset = assetAddress.rawValue,
            totalAssetAmount = data?.totalAssetAmount?.rawValue,
            ignoredHolderAddresses = ignoredHolderAddresses.mapTo(HashSet()) { it.rawValue },
            assetSnapshotMerkleRoot = data?.merkleRootHash?.value,
            assetSnapshotMerkleDepth = data?.merkleTreeDepth,
            assetSnapshotBlockNumber = blockNumber.value,
            assetSnapshotMerkleIpfsHash = data?.merkleTreeIpfsHash?.value
        )
}

data class FullSnapshotData(
    val totalAssetAmount: Balance,
    val merkleRootHash: Hash,
    val merkleTreeIpfsHash: IpfsHash,
    val merkleTreeDepth: Int,
    val hashFn: HashFunction
)
