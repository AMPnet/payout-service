package com.ampnet.payoutservice.model.result

import com.ampnet.payoutservice.blockchain.PayoutStruct
import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.Hash
import com.ampnet.payoutservice.util.IpfsHash
import com.ampnet.payoutservice.util.WalletAddress
import org.web3j.utils.Numeric
import java.math.BigInteger

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
        ignoredAssetAddresses = struct.ignoredAssetAddresses.mapTo(HashSet()) { WalletAddress(it) },
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
}

data class PayoutKey(
    val asset: ContractAddress,
    val payoutBlockNumber: BlockNumber,
    val owner: WalletAddress,
    val merkleRootHash: Hash,
    val totalAssetAmount: Balance
)
