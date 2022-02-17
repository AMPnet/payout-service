package com.ampnet.payoutservice.model

import com.ampnet.payoutservice.util.MerkleTree
import java.util.UUID

data class MerkleTreeWithId(val treeId: UUID, val tree: MerkleTree)
