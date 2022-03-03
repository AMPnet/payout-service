package com.ampnet.payoutservice.util

enum class PayoutStatus(val toTaskStatus: TaskStatus) {
    PROOF_PENDING(TaskStatus.PENDING),
    PROOF_FAILED(TaskStatus.FAILED),
    PROOF_CREATED(TaskStatus.SUCCESS),
    PAYOUT_CREATED(TaskStatus.SUCCESS);

    companion object {
        fun fromTaskStatus(value: TaskStatus): PayoutStatus {
            return when (value) {
                TaskStatus.PENDING -> PROOF_PENDING
                TaskStatus.SUCCESS -> PROOF_CREATED
                TaskStatus.FAILED -> PROOF_FAILED
            }
        }
    }
}
