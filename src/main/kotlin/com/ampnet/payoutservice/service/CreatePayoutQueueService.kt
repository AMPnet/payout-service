package com.ampnet.payoutservice.service

import com.ampnet.payoutservice.model.params.CreateSnapshotParams
import com.ampnet.payoutservice.model.result.FullCreatePayoutTask
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.SnapshotStatus
import com.ampnet.payoutservice.util.WalletAddress
import java.util.UUID

interface CreatePayoutQueueService {
    fun submitTask(params: CreateSnapshotParams): UUID
    fun getTaskById(taskId: UUID): FullCreatePayoutTask?

    fun getAllTasksByIssuerAndOwner(
        chainId: ChainId,
        issuer: ContractAddress?,
        owner: WalletAddress?,
        statuses: Set<SnapshotStatus>
    ): List<FullCreatePayoutTask>
}
