package com.ampnet.payoutservice.model.result

import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.IpfsHash
import com.ampnet.payoutservice.util.SnapshotFailureCause
import com.ampnet.payoutservice.util.SnapshotStatus
import com.ampnet.payoutservice.util.WalletAddress
import java.util.UUID

data class Snapshot(
    val id: UUID,
    val name: String,
    val chainId: ChainId,
    val assetAddress: ContractAddress,
    val blockNumber: BlockNumber,
    val ignoredHolderAddresses: Set<WalletAddress>,
    val ownerAddress: WalletAddress,
    val data: OptionalSnapshotData
)

sealed interface OptionalSnapshotData {
    val status: SnapshotStatus
    val failureCause: SnapshotFailureCause?
}

data class SuccessfulSnapshotData(
    val merkleTreeRootId: UUID,
    val merkleTreeIpfsHash: IpfsHash,
    val totalAssetAmount: Balance,
    override val status: SnapshotStatus = SnapshotStatus.SUCCESS,
    override val failureCause: SnapshotFailureCause? = null
) : OptionalSnapshotData

data class OtherSnapshotData(
    override val status: SnapshotStatus,
    override val failureCause: SnapshotFailureCause?
) : OptionalSnapshotData
