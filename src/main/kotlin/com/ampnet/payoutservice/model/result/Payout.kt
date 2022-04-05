package com.ampnet.payoutservice.model.result

import com.ampnet.payoutservice.blockchain.PayoutStruct
import com.ampnet.payoutservice.controller.response.PayoutResponse
import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.Hash
import com.ampnet.payoutservice.util.IpfsHash
import com.ampnet.payoutservice.util.PayoutStatus
import com.ampnet.payoutservice.util.WalletAddress
import org.web3j.utils.Numeric
import java.math.BigInteger
import java.util.UUID

data class Payout(
    val payoutId: BigInteger,
    val payoutOwner: WalletAddress,
    val payoutInfo: String,
    val isCanceled: Boolean,
    val asset: ContractAddress,
    val totalAssetAmount: Balance,
    val ignoredAssetAddresses: Set<WalletAddress>,
    val assetSnapshotMerkleRoot: Hash,
    val assetSnapshotMerkleDepth: BigInteger,
    val assetSnapshotBlockNumber: BlockNumber,
    val assetSnapshotMerkleIpfsHash: IpfsHash,
    val rewardAsset: ContractAddress,
    val totalRewardAmount: Balance,
    val remainingRewardAmount: Balance
) {
    constructor(struct: PayoutStruct) : this(
        payoutId = struct.payoutId,
        payoutOwner = WalletAddress(struct.payoutOwner),
        payoutInfo = struct.payoutInfo,
        isCanceled = struct.isCanceled,
        asset = ContractAddress(struct.asset),
        totalAssetAmount = Balance(struct.totalAssetAmount),
        ignoredAssetAddresses = struct.ignoredHolderAddresses.mapTo(HashSet()) { WalletAddress(it) },
        assetSnapshotMerkleRoot = Hash(Numeric.toHexString(struct.assetSnapshotMerkleRoot)),
        assetSnapshotMerkleDepth = struct.assetSnapshotMerkleDepth,
        assetSnapshotBlockNumber = BlockNumber(struct.assetSnapshotBlockNumber),
        assetSnapshotMerkleIpfsHash = IpfsHash(struct.assetSnapshotMerkleIpfsHash),
        rewardAsset = ContractAddress(struct.rewardAsset),
        totalRewardAmount = Balance(struct.totalRewardAmount),
        remainingRewardAmount = Balance(struct.remainingRewardAmount)
    )

    fun toKey(): PayoutKey =
        PayoutKey(
            asset = asset,
            payoutBlockNumber = assetSnapshotBlockNumber,
            owner = payoutOwner,
            merkleRootHash = assetSnapshotMerkleRoot,
            totalAssetAmount = totalAssetAmount
        )

    fun toPayoutResponse(taskId: UUID?, issuer: String?): PayoutResponse =
        PayoutResponse(
            taskId = taskId,
            status = PayoutStatus.PAYOUT_CREATED,
            issuer = issuer,

            payoutId = payoutId,
            payoutOwner = payoutOwner.rawValue,
            payoutInfo = payoutInfo,
            isCanceled = isCanceled,

            asset = asset.rawValue,
            totalAssetAmount = totalAssetAmount.rawValue,
            ignoredHolderAddresses = ignoredAssetAddresses.mapTo(HashSet()) { it.rawValue },

            assetSnapshotMerkleRoot = assetSnapshotMerkleRoot.value,
            assetSnapshotMerkleDepth = assetSnapshotMerkleDepth.intValueExact(),
            assetSnapshotBlockNumber = assetSnapshotBlockNumber.value,
            assetSnapshotMerkleIpfsHash = assetSnapshotMerkleIpfsHash.value,

            rewardAsset = rewardAsset.rawValue,
            totalRewardAmount = totalRewardAmount.rawValue,
            remainingRewardAmount = remainingRewardAmount.rawValue
        )
}

data class PayoutKey(
    val asset: ContractAddress,
    val payoutBlockNumber: BlockNumber,
    val owner: WalletAddress,
    val merkleRootHash: Hash,
    val totalAssetAmount: Balance
)
