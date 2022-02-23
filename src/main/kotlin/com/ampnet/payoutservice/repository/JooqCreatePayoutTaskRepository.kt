package com.ampnet.payoutservice.repository

import com.ampnet.payoutservice.generated.jooq.tables.records.CreatePayoutTaskRecord
import com.ampnet.payoutservice.model.params.CreatePayoutTaskParams
import com.ampnet.payoutservice.model.result.CreatePayoutTask
import com.ampnet.payoutservice.model.result.OtherTaskData
import com.ampnet.payoutservice.model.result.PendingCreatePayoutTask
import com.ampnet.payoutservice.model.result.SuccessfulTaskData
import com.ampnet.payoutservice.service.UuidProvider
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.IpfsHash
import com.ampnet.payoutservice.util.TaskStatus
import com.ampnet.payoutservice.util.WalletAddress
import mu.KLogging
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.math.BigInteger
import java.util.UUID
import com.ampnet.payoutservice.generated.jooq.enums.TaskStatus as DbTaskStatus
import com.ampnet.payoutservice.generated.jooq.tables.CreatePayoutTask as CreatePayoutTaskTable

@Repository
class JooqCreatePayoutTaskRepository(private val dslContext: DSLContext, private val uuidProvider: UuidProvider) :
    CreatePayoutTaskRepository {

    companion object : KLogging()

    override fun getById(taskId: UUID): CreatePayoutTask? {
        logger.info { "Fetching create payout task, taskId: $taskId" }
        return dslContext.selectFrom(CreatePayoutTaskTable.CREATE_PAYOUT_TASK)
            .where(CreatePayoutTaskTable.CREATE_PAYOUT_TASK.ID.eq(taskId))
            .fetchOne()
            ?.toModel()
    }

    override fun getAllByChainIdIssuerOwnerAndStatuses(
        chainId: ChainId,
        issuer: ContractAddress?,
        owner: WalletAddress?,
        statuses: List<TaskStatus>
    ): List<CreatePayoutTask> {
        logger.info {
            "Fetching all create payout tasks for chainId: $chainId, issuer: $issuer," +
                " owner: $owner, statuses: $statuses"
        }

        val chainIdCondition = CreatePayoutTaskTable.CREATE_PAYOUT_TASK.CHAIN_ID.eq(chainId.value)
        val issuerCondition = issuer?.let { CreatePayoutTaskTable.CREATE_PAYOUT_TASK.ISSUER_ADDRESS.eq(it.rawValue) }
        val ownerCondition = owner?.let { CreatePayoutTaskTable.CREATE_PAYOUT_TASK.REQUESTER_ADDRESS.eq(it.rawValue) }
        val dbStatuses = statuses.map { it.toDbEnum }
        val statusesCondition = dbStatuses.takeIf { it.isNotEmpty() }
            ?.let { CreatePayoutTaskTable.CREATE_PAYOUT_TASK.STATUS.`in`(it) }
        val conditions = listOfNotNull(chainIdCondition, issuerCondition, ownerCondition, statusesCondition)

        return dslContext.selectFrom(CreatePayoutTaskTable.CREATE_PAYOUT_TASK)
            .where(DSL.and(conditions))
            .fetch { it.toModel() }
    }

    override fun createPayoutTask(params: CreatePayoutTaskParams): UUID {
        logger.info { "Storing create payout task, params: $params" }

        val taskId = uuidProvider.getUuid()

        dslContext.executeInsert(
            CreatePayoutTaskRecord(
                id = taskId,
                chainId = params.chainId.value,
                assetAddress = params.assetAddress.rawValue,
                blockNumber = params.payoutBlock.value,
                ignoredAssetAddresses = params.ignoredAssetAddresses.map { it.rawValue }.toTypedArray(),
                requesterAddress = params.requesterAddress.rawValue,
                issuerAddress = params.issuerAddress?.rawValue,
                status = DbTaskStatus.PENDING,
                resultTree = null,
                treeIpfsHash = null,
                totalAssetAmount = null
            )
        )

        return taskId
    }

    override fun getPending(): PendingCreatePayoutTask? {
        return dslContext.selectFrom(CreatePayoutTaskTable.CREATE_PAYOUT_TASK)
            .where(CreatePayoutTaskTable.CREATE_PAYOUT_TASK.STATUS.eq(DbTaskStatus.PENDING))
            .limit(1)
            .forUpdate()
            .skipLocked()
            .fetchOne()?.let {
                PendingCreatePayoutTask(
                    taskId = it.id!!,
                    chainId = ChainId(it.chainId!!),
                    assetAddress = ContractAddress(it.assetAddress!!),
                    blockNumber = BlockNumber(it.blockNumber!!),
                    ignoredAssetAddresses = it.ignoredAssetAddresses!!.mapTo(HashSet()) { a -> WalletAddress(a!!) },
                    requesterAddress = WalletAddress(it.requesterAddress!!),
                    issuerAddress = it.issuerAddress?.let { i -> ContractAddress(i) }
                )
            }
    }

    override fun completeTask(
        taskId: UUID,
        merkleTreeRootId: UUID,
        merkleTreeIpfsHash: IpfsHash,
        totalAssetAmount: BigInteger
    ): CreatePayoutTask? {
        logger.info {
            "Marking task as success, taskId: $taskId, merkleTreeRootId: $merkleTreeRootId," +
                " merkleTreeIpfsHash: $merkleTreeIpfsHash, totalAssetAmount: $totalAssetAmount"
        }
        return dslContext.update(CreatePayoutTaskTable.CREATE_PAYOUT_TASK)
            .set(CreatePayoutTaskTable.CREATE_PAYOUT_TASK.STATUS, DbTaskStatus.SUCCESS)
            .set(CreatePayoutTaskTable.CREATE_PAYOUT_TASK.RESULT_TREE, merkleTreeRootId)
            .set(CreatePayoutTaskTable.CREATE_PAYOUT_TASK.TREE_IPFS_HASH, merkleTreeIpfsHash.value)
            .set(CreatePayoutTaskTable.CREATE_PAYOUT_TASK.TOTAL_ASSET_AMOUNT, totalAssetAmount)
            .where(CreatePayoutTaskTable.CREATE_PAYOUT_TASK.ID.eq(taskId))
            .returning()
            .fetchOne()
            ?.toModel()
    }

    override fun failTask(taskId: UUID): CreatePayoutTask? {
        logger.info { "Marking task as failed, taskId: $taskId" }
        return dslContext.update(CreatePayoutTaskTable.CREATE_PAYOUT_TASK)
            .set(CreatePayoutTaskTable.CREATE_PAYOUT_TASK.STATUS, DbTaskStatus.FAILED)
            .where(CreatePayoutTaskTable.CREATE_PAYOUT_TASK.ID.eq(taskId))
            .returning()
            .fetchOne()
            ?.toModel()
    }

    private fun CreatePayoutTaskRecord.toModel(): CreatePayoutTask {
        val taskStatus = TaskStatus.fromDbEnum(status!!)
        val taskData = if (taskStatus == TaskStatus.SUCCESS) {
            SuccessfulTaskData(
                merkleTreeRootId = resultTree!!,
                merkleTreeIpfsHash = IpfsHash(treeIpfsHash!!),
                totalAssetAmount = totalAssetAmount!!
            )
        } else OtherTaskData(taskStatus)

        return CreatePayoutTask(
            taskId = id!!,
            chainId = ChainId(chainId!!),
            assetAddress = ContractAddress(assetAddress!!),
            blockNumber = BlockNumber(blockNumber!!),
            ignoredAssetAddresses = ignoredAssetAddresses!!.mapTo(HashSet()) { WalletAddress(it!!) },
            requesterAddress = WalletAddress(requesterAddress!!),
            issuerAddress = issuerAddress?.let { ContractAddress(it) },
            data = taskData
        )
    }
}
