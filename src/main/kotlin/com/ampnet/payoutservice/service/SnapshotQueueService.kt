package com.ampnet.payoutservice.service

import com.ampnet.payoutservice.model.params.CreateSnapshotParams
import com.ampnet.payoutservice.model.result.FullSnapshot
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.SnapshotStatus
import com.ampnet.payoutservice.util.WalletAddress
import java.util.UUID

interface SnapshotQueueService {
    fun submitSnapshot(params: CreateSnapshotParams): UUID
    fun getSnapshotById(snapshotId: UUID): FullSnapshot?

    fun getAllSnapshotsByChainIdOwnerAndStatuses(
        chainId: ChainId?,
        owner: WalletAddress?,
        statuses: Set<SnapshotStatus>
    ): List<FullSnapshot>
}
