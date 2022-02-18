package com.ampnet.payoutservice.service

import com.ampnet.payoutservice.controller.response.CreatePayoutTaskResponse
import com.ampnet.payoutservice.model.params.CreatePayoutTaskParams
import java.util.UUID

interface CreatePayoutQueueService { // TODO test
    fun submitTask(params: CreatePayoutTaskParams): UUID
    fun getTaskById(taskId: UUID): CreatePayoutTaskResponse?
}
