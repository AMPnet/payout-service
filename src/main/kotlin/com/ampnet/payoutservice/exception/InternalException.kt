package com.ampnet.payoutservice.exception

class InternalException(val errorCode: ErrorCode, exceptionMessage: String) : Exception(exceptionMessage) {
    companion object {
        private const val serialVersionUID: Long = -2656013978175834059L
    }
}
