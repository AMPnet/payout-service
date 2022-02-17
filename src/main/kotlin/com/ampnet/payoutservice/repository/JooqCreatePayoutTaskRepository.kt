package com.ampnet.payoutservice.repository

import com.ampnet.payoutservice.generated.jooq.tables.records.CreatePayoutTaskRecord
import com.ampnet.payoutservice.model.CreatePayoutTask
import com.ampnet.payoutservice.model.OtherTaskData
import com.ampnet.payoutservice.model.PendingCreatePayoutTask
import com.ampnet.payoutservice.model.SuccessfulTaskData
import com.ampnet.payoutservice.service.UuidProvider
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.IpfsHash
import com.ampnet.payoutservice.util.TaskStatus
import com.ampnet.payoutservice.util.WalletAddress
import mu.KLogging
import org.jooq.DSLContext
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

        val task = dslContext.selectFrom(CreatePayoutTaskTable.CREATE_PAYOUT_TASK)
            .where(CreatePayoutTaskTable.CREATE_PAYOUT_TASK.ID.eq(taskId))
            .fetchOne() ?: return null

        val taskStatus = TaskStatus.fromDbEnum(task.status!!)
        val taskData = if (taskStatus == TaskStatus.SUCCESS) {
            SuccessfulTaskData(
                merkleTreeRootId = task.resultTree!!,
                merkleTreeIpfsHash = IpfsHash(task.treeIpfsHash!!),
                totalAssetAmount = task.totalAssetAmount!!
            )
        } else OtherTaskData(taskStatus)

        return CreatePayoutTask(
            taskId = task.id!!,
            chainId = ChainId(task.chainId!!),
            assetAddress = ContractAddress(task.assetAddress!!),
            blockNumber = BlockNumber(task.blockNumber!!),
            ignoredAssetAddresses = task.ignoredAssetAddresses!!.mapTo(HashSet()) { WalletAddress(it!!) },
            requesterAddress = WalletAddress(task.requesterAddress!!),
            issuerAddress = task.issuerAddress?.let { ContractAddress(it) },
            data = taskData
        )
    }

    override fun createPayoutTask(
        chainId: ChainId,
        assetAddress: ContractAddress,
        requesterAddress: WalletAddress,
        issuerAddress: ContractAddress?,
        payoutBlock: BlockNumber,
        ignoredAssetAddresses: Set<WalletAddress>
    ): UUID {
        logger.info {
            "Storing create payout task, chainId: $chainId, assetAddress: $assetAddress," +
                " requesterAddress: $requesterAddress, issuerAddress: $issuerAddress, payoutBlock: $payoutBlock," +
                " ignoredAssetAddresses: $ignoredAssetAddresses"
        }

        val taskId = uuidProvider.getUuid()

        dslContext.executeInsert(
            CreatePayoutTaskRecord(
                id = taskId,
                chainId = chainId.value,
                assetAddress = assetAddress.rawValue,
                blockNumber = payoutBlock.value,
                ignoredAssetAddresses = ignoredAssetAddresses.map { it.rawValue }.toTypedArray(),
                requesterAddress = requesterAddress.rawValue,
                issuerAddress = issuerAddress?.rawValue,
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
    ) {
        logger.info {
            "Marking task as success, taskId: $taskId, merkleTreeRootId: $merkleTreeRootId," +
                " merkleTreeIpfsHash: $merkleTreeIpfsHash, totalAssetAmount: $totalAssetAmount"
        }
        dslContext.update(CreatePayoutTaskTable.CREATE_PAYOUT_TASK)
            .set(CreatePayoutTaskTable.CREATE_PAYOUT_TASK.STATUS, DbTaskStatus.SUCCESS)
            .set(CreatePayoutTaskTable.CREATE_PAYOUT_TASK.RESULT_TREE, merkleTreeRootId)
            .set(CreatePayoutTaskTable.CREATE_PAYOUT_TASK.TREE_IPFS_HASH, merkleTreeIpfsHash.value)
            .set(CreatePayoutTaskTable.CREATE_PAYOUT_TASK.TOTAL_ASSET_AMOUNT, totalAssetAmount)
            .execute()
    }

    override fun failTask(taskId: UUID) {
        logger.info { "Marking task as failed, taskId: $taskId" }
        dslContext.update(CreatePayoutTaskTable.CREATE_PAYOUT_TASK)
            .set(CreatePayoutTaskTable.CREATE_PAYOUT_TASK.STATUS, DbTaskStatus.FAILED)
            .execute()
    }
}
