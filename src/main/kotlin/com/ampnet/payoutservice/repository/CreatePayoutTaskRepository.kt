package com.ampnet.payoutservice.repository

import com.ampnet.payoutservice.model.params.CreatePayoutTaskParams
import com.ampnet.payoutservice.model.result.CreatePayoutTask
import com.ampnet.payoutservice.model.result.PendingCreatePayoutTask
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.IpfsHash
import com.ampnet.payoutservice.util.WalletAddress
import java.math.BigInteger
import java.util.UUID

interface CreatePayoutTaskRepository {
    fun getById(taskId: UUID): CreatePayoutTask?
    fun getAllByIssuerAndOwner(issuer: ContractAddress?, owner: WalletAddress?): List<CreatePayoutTask>
    fun createPayoutTask(params: CreatePayoutTaskParams): UUID
    fun getPending(): PendingCreatePayoutTask?

    fun completeTask(
        taskId: UUID,
        merkleTreeRootId: UUID,
        merkleTreeIpfsHash: IpfsHash,
        totalAssetAmount: BigInteger
    ): CreatePayoutTask?

    fun failTask(taskId: UUID): CreatePayoutTask?
}
