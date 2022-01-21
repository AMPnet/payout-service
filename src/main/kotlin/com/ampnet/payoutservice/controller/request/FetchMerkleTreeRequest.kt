package com.ampnet.payoutservice.controller.request

import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.Hash
import java.math.BigInteger

data class RawFetchMerkleTreeRequest(
    val rootHash: String,
    val chainId: Long,
    val contractAddress: String,
    val blockNumber: BigInteger
) {
    fun stronglyTyped() = FetchMerkleTreeRequest(
        Hash(rootHash),
        ChainId(chainId),
        ContractAddress(contractAddress),
        BlockNumber(blockNumber)
    )
}

data class FetchMerkleTreeRequest(
    val rootHash: Hash,
    val chainId: ChainId,
    val contractAddress: ContractAddress,
    val blockNumber: BlockNumber
)
