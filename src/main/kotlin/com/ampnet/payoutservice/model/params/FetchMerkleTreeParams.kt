package com.ampnet.payoutservice.model.params

import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.Hash

data class FetchMerkleTreeParams(
    val rootHash: Hash,
    val chainId: ChainId,
    val assetAddress: ContractAddress
)
