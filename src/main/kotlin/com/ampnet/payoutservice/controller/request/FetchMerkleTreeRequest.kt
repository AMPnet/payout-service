package com.ampnet.payoutservice.controller.request

import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.Hash

data class FetchMerkleTreeRequest(
    val rootHash: Hash,
    val chainId: ChainId,
    val assetAddress: ContractAddress
)
