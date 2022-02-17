package com.ampnet.payoutservice.repository

import com.ampnet.payoutservice.controller.request.FetchMerkleTreePathRequest
import com.ampnet.payoutservice.controller.request.FetchMerkleTreeRequest
import com.ampnet.payoutservice.model.MerkleTreeWithId
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.MerkleTree
import java.util.UUID

interface MerkleTreeRepository {
    fun getById(treeId: UUID): MerkleTree?
    fun storeTree(tree: MerkleTree, chainId: ChainId, assetAddress: ContractAddress, blockNumber: BlockNumber): UUID
    fun fetchTree(request: FetchMerkleTreeRequest): MerkleTreeWithId?
    fun containsAddress(request: FetchMerkleTreePathRequest): Boolean
}
