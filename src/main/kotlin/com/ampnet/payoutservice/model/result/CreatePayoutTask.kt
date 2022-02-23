package com.ampnet.payoutservice.model.result

import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.IpfsHash
import com.ampnet.payoutservice.util.TaskStatus
import com.ampnet.payoutservice.util.WalletAddress
import java.math.BigInteger
import java.util.UUID

data class CreatePayoutTask(
    val taskId: UUID,
    val chainId: ChainId,
    val assetAddress: ContractAddress,
    val blockNumber: BlockNumber,
    val ignoredAssetAddresses: Set<WalletAddress>,
    val requesterAddress: WalletAddress,
    val issuerAddress: ContractAddress?,
    val data: OptionalCreatePayoutTaskData
)

sealed interface OptionalCreatePayoutTaskData {
    val status: TaskStatus
}

data class SuccessfulTaskData(
    val merkleTreeRootId: UUID,
    val merkleTreeIpfsHash: IpfsHash,
    val totalAssetAmount: BigInteger,
    override val status: TaskStatus = TaskStatus.SUCCESS
) : OptionalCreatePayoutTaskData

data class OtherTaskData(override val status: TaskStatus) : OptionalCreatePayoutTaskData
