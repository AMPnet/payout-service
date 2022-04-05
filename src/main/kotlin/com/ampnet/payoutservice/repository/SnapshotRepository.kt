package com.ampnet.payoutservice.repository

import com.ampnet.payoutservice.model.params.CreateSnapshotParams
import com.ampnet.payoutservice.model.result.PendingSnapshot
import com.ampnet.payoutservice.model.result.Snapshot
import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.IpfsHash
import com.ampnet.payoutservice.util.SnapshotStatus
import com.ampnet.payoutservice.util.WalletAddress
import java.util.UUID

interface SnapshotRepository {
    fun getById(snapshotId: UUID): Snapshot?

    fun getAllByChainIdOwnerAndStatuses(
        chainId: ChainId?,
        owner: WalletAddress?,
        statuses: Set<SnapshotStatus>
    ): List<Snapshot>

    fun createSnapshot(params: CreateSnapshotParams): UUID
    fun getPending(): PendingSnapshot?

    fun completeSnapshot(
        snapshotId: UUID,
        merkleTreeRootId: UUID,
        merkleTreeIpfsHash: IpfsHash,
        totalAssetAmount: Balance
    ): Snapshot?

    fun failSnapshot(snapshotId: UUID): Snapshot?
}
