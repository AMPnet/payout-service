package com.ampnet.payoutservice.service

import com.ampnet.payoutservice.model.result.FullCreatePayoutTask
import com.ampnet.payoutservice.model.params.CreatePayoutTaskParams
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.WalletAddress
import java.util.UUID

interface CreatePayoutQueueService {
    fun submitTask(params: CreatePayoutTaskParams): UUID
    fun getTaskById(taskId: UUID): FullCreatePayoutTask?
    fun getAllTasksByIssuerAndOwner(issuer: ContractAddress?, owner: WalletAddress?): List<FullCreatePayoutTask>
}
