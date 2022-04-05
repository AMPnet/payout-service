package com.ampnet.payoutservice.repository

import com.ampnet.payoutservice.generated.jooq.tables.records.SnapshotRecord
import com.ampnet.payoutservice.model.params.CreateSnapshotParams
import com.ampnet.payoutservice.model.result.OtherSnapshotData
import com.ampnet.payoutservice.model.result.PendingSnapshot
import com.ampnet.payoutservice.model.result.Snapshot
import com.ampnet.payoutservice.model.result.SuccessfulSnapshotData
import com.ampnet.payoutservice.service.UuidProvider
import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.IpfsHash
import com.ampnet.payoutservice.util.SnapshotStatus
import com.ampnet.payoutservice.util.WalletAddress
import mu.KLogging
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.util.UUID
import com.ampnet.payoutservice.generated.jooq.enums.SnapshotStatus as DbSnapshotStatus
import com.ampnet.payoutservice.generated.jooq.tables.Snapshot as SnapshotTable

@Repository
class JooqSnapshotRepository(private val dslContext: DSLContext, private val uuidProvider: UuidProvider) :
    SnapshotRepository {

    companion object : KLogging()

    override fun getById(snapshotId: UUID): Snapshot? {
        logger.debug { "Fetching snapshot, snapshotId: $snapshotId" }
        return dslContext.selectFrom(SnapshotTable.SNAPSHOT)
            .where(SnapshotTable.SNAPSHOT.ID.eq(snapshotId))
            .fetchOne()
            ?.toModel()
    }

    override fun getAllByChainIdOwnerAndStatuses(
        chainId: ChainId?,
        owner: WalletAddress?,
        statuses: Set<SnapshotStatus>
    ): List<Snapshot> {
        logger.debug { "Fetching all snapshots for chainId: $chainId owner: $owner, statuses: $statuses" }

        val chainIdCondition = chainId?.let { SnapshotTable.SNAPSHOT.CHAIN_ID.eq(it.value) }
        val ownerCondition = owner?.let { SnapshotTable.SNAPSHOT.OWNER_ADDRESS.eq(it.rawValue) }
        val dbStatuses = statuses.map { it.toDbEnum }
        val statusesCondition = dbStatuses.takeIf { it.isNotEmpty() }
            ?.let { SnapshotTable.SNAPSHOT.STATUS.`in`(it) }
        val conditions = listOfNotNull(chainIdCondition, ownerCondition, statusesCondition)

        return dslContext.selectFrom(SnapshotTable.SNAPSHOT)
            .where(DSL.and(conditions))
            .fetch { it.toModel() }
    }

    override fun createSnapshot(params: CreateSnapshotParams): UUID {
        logger.info { "Storing pending snapshot, params: $params" }

        val snapshotId = uuidProvider.getUuid()

        dslContext.executeInsert(
            SnapshotRecord(
                id = snapshotId,
                name = params.name,
                chainId = params.chainId.value,
                assetAddress = params.assetAddress.rawValue,
                blockNumber = params.payoutBlock.value,
                ignoredHolderAddresses = params.ignoredHolderAddresses.map { it.rawValue }.toTypedArray(),
                ownerAddress = params.ownerAddress.rawValue,
                status = DbSnapshotStatus.PENDING,
                resultTree = null,
                treeIpfsHash = null,
                totalAssetAmount = null
            )
        )

        return snapshotId
    }

    override fun getPending(): PendingSnapshot? {
        return dslContext.selectFrom(SnapshotTable.SNAPSHOT)
            .where(SnapshotTable.SNAPSHOT.STATUS.eq(DbSnapshotStatus.PENDING))
            .limit(1)
            .forUpdate()
            .skipLocked()
            .fetchOne()?.let {
                PendingSnapshot(
                    id = it.id!!,
                    name = it.name!!,
                    chainId = ChainId(it.chainId!!),
                    assetAddress = ContractAddress(it.assetAddress!!),
                    blockNumber = BlockNumber(it.blockNumber!!),
                    ignoredHolderAddresses = it.ignoredHolderAddresses!!.mapTo(HashSet()) { a -> WalletAddress(a!!) },
                    ownerAddress = WalletAddress(it.ownerAddress!!)
                )
            }
    }

    override fun completeSnapshot(
        snapshotId: UUID,
        merkleTreeRootId: UUID,
        merkleTreeIpfsHash: IpfsHash,
        totalAssetAmount: Balance
    ): Snapshot? {
        logger.info {
            "Marking snapshot as success, snapshotId: $snapshotId, merkleTreeRootId: $merkleTreeRootId," +
                " merkleTreeIpfsHash: $merkleTreeIpfsHash, totalAssetAmount: $totalAssetAmount"
        }
        return dslContext.update(SnapshotTable.SNAPSHOT)
            .set(SnapshotTable.SNAPSHOT.STATUS, DbSnapshotStatus.SUCCESS)
            .set(SnapshotTable.SNAPSHOT.RESULT_TREE, merkleTreeRootId)
            .set(SnapshotTable.SNAPSHOT.TREE_IPFS_HASH, merkleTreeIpfsHash.value)
            .set(SnapshotTable.SNAPSHOT.TOTAL_ASSET_AMOUNT, totalAssetAmount.rawValue)
            .where(SnapshotTable.SNAPSHOT.ID.eq(snapshotId))
            .returning()
            .fetchOne()
            ?.toModel()
    }

    override fun failSnapshot(snapshotId: UUID): Snapshot? {
        logger.info { "Marking snapshot as failed, snapshotId: $snapshotId" }
        return dslContext.update(SnapshotTable.SNAPSHOT)
            .set(SnapshotTable.SNAPSHOT.STATUS, DbSnapshotStatus.FAILED)
            .where(SnapshotTable.SNAPSHOT.ID.eq(snapshotId))
            .returning()
            .fetchOne()
            ?.toModel()
    }

    private fun SnapshotRecord.toModel(): Snapshot {
        val snapshotStatus = SnapshotStatus.fromDbEnum(status!!)
        val snapshotData = if (snapshotStatus == SnapshotStatus.SUCCESS) {
            SuccessfulSnapshotData(
                merkleTreeRootId = resultTree!!,
                merkleTreeIpfsHash = IpfsHash(treeIpfsHash!!),
                totalAssetAmount = Balance(totalAssetAmount!!)
            )
        } else OtherSnapshotData(snapshotStatus)

        return Snapshot(
            id = id!!,
            name = name!!,
            chainId = ChainId(chainId!!),
            assetAddress = ContractAddress(assetAddress!!),
            blockNumber = BlockNumber(blockNumber!!),
            ignoredHolderAddresses = ignoredHolderAddresses!!.mapTo(HashSet()) { WalletAddress(it!!) },
            ownerAddress = WalletAddress(ownerAddress!!),
            data = snapshotData
        )
    }
}
