package com.ampnet.payoutservice.service

import com.ampnet.payoutservice.controller.response.CreatePayoutTaskResponse
import com.ampnet.payoutservice.model.params.CreatePayoutTaskParams
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.WalletAddress
import java.util.UUID

interface CreatePayoutQueueService {
    fun submitTask(params: CreatePayoutTaskParams): UUID
    fun getTaskById(taskId: UUID): CreatePayoutTaskResponse?
    fun getAllTasksByIssuerAndOwner(issuer: ContractAddress?, owner: WalletAddress?): List<CreatePayoutTaskResponse>
}
