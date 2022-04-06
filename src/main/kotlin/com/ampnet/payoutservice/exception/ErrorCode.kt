package com.ampnet.payoutservice.exception

enum class ErrorCode(val categoryCode: String, var specificCode: String, var message: String) {
    // User: 03
    USER_NOT_ASSET_OWNER("03", "03", "User is not asset owner"),

    // Blockchain: 04
    BLOCKCHAIN_ID("04", "01", "Blockchain id not supported"),
    BLOCKCHAIN_CONTRACT_READ_ERROR("04", "04", "Blockchain contract read error"),
    BLOCKCHAIN_CONTRACT_EVENT_READ_ERROR("04", "05", "Blockchain contract event read error"),

    // IPFS: 05
    IPFS_UPLOAD_FAILED("05", "01", "IPFS upload failed"),

    // Payout: 06
    SNAPSHOT_NOT_FOUND("06", "01", "Snapshot not found"),
    PAYOUT_MERKLE_TREE_NOT_FOUND("06", "02", "Merkle tree not found for specified payout parameters"),
    PAYOUT_NOT_FOUND_FOR_ACCOUNT("06", "03", "Payout does not exist for specified account")
}
