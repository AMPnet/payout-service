package com.ampnet.payoutservice.exception

enum class ErrorCode(val categoryCode: String, var specificCode: String, var message: String) {
    // Blockchain: 04
    BLOCKCHAIN_ID("04", "01", "Blockchain id not supported"),
    BLOCKCHAIN_CONTRACT_READ_ERROR("04", "04", "Blockchain contract read error"),
    BLOCKCHAIN_CONTRACT_EVENT_READ_ERROR("04", "05", "Blockchain contract event read error"),

    // IPFS: 05
    IPFS_UPLOAD_FAILED("05", "01", "IPFS upload failed")
}
