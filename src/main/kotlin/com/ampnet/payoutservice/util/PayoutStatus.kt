package com.ampnet.payoutservice.util

enum class PayoutStatus(val toTaskStatus: TaskStatus) {
    PROOF_PENDING(TaskStatus.PENDING),
    PROOF_FAILED(TaskStatus.FAILED),
    PROOF_CREATED(TaskStatus.SUCCESS),
    PAYOUT_CREATED(TaskStatus.SUCCESS)
}
