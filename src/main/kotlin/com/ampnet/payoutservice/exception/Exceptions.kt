package com.ampnet.payoutservice.exception

class ResourceNotFoundException(val errorCode: ErrorCode, exceptionMessage: String) : Exception(exceptionMessage) {
    companion object {
        private const val serialVersionUID: Long = 6281563419564137017L
    }
}

class InternalException(val errorCode: ErrorCode, exceptionMessage: String) : Exception(exceptionMessage) {
    companion object {
        private const val serialVersionUID: Long = -2656013978175834059L
    }
}

class InvalidRequestException(
    val errorCode: ErrorCode,
    exceptionMessage: String,
    throwable: Throwable? = null
) : Exception(exceptionMessage, throwable) {
    companion object {
        private const val serialVersionUID: Long = 959044880750168366L
    }
}
