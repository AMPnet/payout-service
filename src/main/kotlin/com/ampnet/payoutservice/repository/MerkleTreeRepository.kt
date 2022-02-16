package com.ampnet.payoutservice.repository

import com.ampnet.payoutservice.controller.request.FetchMerkleTreePathRequest
import com.ampnet.payoutservice.controller.request.FetchMerkleTreeRequest
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.Hash
import com.ampnet.payoutservice.util.MerkleTree

interface MerkleTreeRepository {

    fun storeTree(tree: MerkleTree, chainId: ChainId, assetAddress: ContractAddress, blockNumber: BlockNumber): Hash

    fun treeExists(rootHash: Hash, chainId: ChainId, assetAddress: ContractAddress): Boolean

    fun fetchTree(request: FetchMerkleTreeRequest): MerkleTree?

    fun containsAddress(request: FetchMerkleTreePathRequest): Boolean
}
