package com.ampnet.payoutservice.controller.request

import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.Hash
import com.ampnet.payoutservice.util.WalletAddress

data class FetchMerkleTreePathRequest(
    val rootHash: Hash,
    val chainId: ChainId,
    val contractAddress: ContractAddress,
    val walletAddress: WalletAddress
) {
    val toFetchMerkleTreeRequest: FetchMerkleTreeRequest
        get() = FetchMerkleTreeRequest(rootHash, chainId, contractAddress)
}
