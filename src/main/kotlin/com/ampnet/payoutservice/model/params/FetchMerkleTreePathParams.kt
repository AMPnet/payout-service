package com.ampnet.payoutservice.model.params

import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.Hash
import com.ampnet.payoutservice.util.WalletAddress

data class FetchMerkleTreePathParams(
    val rootHash: Hash,
    val chainId: ChainId,
    val assetAddress: ContractAddress,
    val walletAddress: WalletAddress
) {
    val toFetchMerkleTreeParams: FetchMerkleTreeParams
        get() = FetchMerkleTreeParams(rootHash, chainId, assetAddress)
}
