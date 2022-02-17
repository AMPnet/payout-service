package com.ampnet.payoutservice.repository

import com.ampnet.payoutservice.generated.jooq.tables.CreatePayoutTask
import com.ampnet.payoutservice.generated.jooq.tables.records.CreatePayoutTaskRecord
import com.ampnet.payoutservice.model.PendingCreatePayoutTask
import com.ampnet.payoutservice.service.UuidProvider
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.IpfsHash
import com.ampnet.payoutservice.util.WalletAddress
import mu.KLogging
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.util.UUID
import com.ampnet.payoutservice.generated.jooq.enums.TaskStatus as DbTaskStatus

@Repository
class JooqCreatePayoutTaskRepository(private val dslContext: DSLContext, private val uuidProvider: UuidProvider) :
    CreatePayoutTaskRepository {

    companion object : KLogging()

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
                treeIpfsHash = null
            )
        )

        return taskId
    }

    override fun getPending(): PendingCreatePayoutTask? {
        return dslContext.selectFrom(CreatePayoutTask.CREATE_PAYOUT_TASK)
            .where(CreatePayoutTask.CREATE_PAYOUT_TASK.STATUS.eq(DbTaskStatus.PENDING))
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

    override fun completeTask(taskId: UUID, merkleTreeRootId: UUID, merkleTreeIpfsHash: IpfsHash) {
        logger.info {
            "Marking task as success, taskId: $taskId, merkleTreeRootId: $merkleTreeRootId," +
                " merkleTreeIpfsHash: $merkleTreeIpfsHash"
        }
        dslContext.update(CreatePayoutTask.CREATE_PAYOUT_TASK)
            .set(CreatePayoutTask.CREATE_PAYOUT_TASK.STATUS, DbTaskStatus.SUCCESS)
            .set(CreatePayoutTask.CREATE_PAYOUT_TASK.RESULT_TREE, merkleTreeRootId)
            .set(CreatePayoutTask.CREATE_PAYOUT_TASK.TREE_IPFS_HASH, merkleTreeIpfsHash.value)
            .execute()
    }

    override fun failTask(taskId: UUID) {
        logger.info { "Marking task as failed, taskId: $taskId" }
        dslContext.update(CreatePayoutTask.CREATE_PAYOUT_TASK)
            .set(CreatePayoutTask.CREATE_PAYOUT_TASK.STATUS, DbTaskStatus.FAILED)
            .execute()
    }
}
