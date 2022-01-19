package com.ampnet.identityservice.exception

enum class ErrorCode(val categoryCode: String, var specificCode: String, var message: String) {
    // Blockchain: 04
    BLOCKCHAIN_ID("04", "01", "Blockchain id not supported")
}
