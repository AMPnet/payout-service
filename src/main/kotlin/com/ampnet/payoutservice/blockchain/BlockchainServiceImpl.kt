package com.ampnet.payoutservice.blockchain

import com.ampnet.payoutservice.blockchain.properties.ChainPropertiesHandler
import com.ampnet.payoutservice.config.ApplicationProperties
import com.ampnet.payoutservice.exception.ErrorCode
import com.ampnet.payoutservice.exception.InternalException
import com.ampnet.payoutservice.util.AccountBalance
import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.WalletAddress
import mu.KLogging
import org.springframework.stereotype.Service
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.RemoteFunctionCall
import org.web3j.tx.ReadonlyTransactionManager
import org.web3j.tx.gas.DefaultGasProvider

@Service
class BlockchainServiceImpl(applicationProperties: ApplicationProperties) : BlockchainService {

    companion object : KLogging()

    private val chainHandler = ChainPropertiesHandler(applicationProperties)

    // TODO write integTest with HardHat
    @Throws(InternalException::class)
    override fun fetchErc20AccountBalances(
        chainId: ChainId,
        erc20ContractAddress: ContractAddress,
        startBlock: BlockNumber?,
        endBlock: BlockNumber
    ): List<AccountBalance> {
        logger.info { "Fetching balances for ERC20 contract: $erc20ContractAddress on chain: $chainId" }
        val blockchainProperties = chainHandler.getBlockchainProperties(chainId)
        val contract = IERC20.load(
            erc20ContractAddress.rawValue,
            blockchainProperties.web3j,
            ReadonlyTransactionManager(blockchainProperties.web3j, erc20ContractAddress.rawValue),
            DefaultGasProvider()
        )
        val startBlockParameter =
            startBlock?.value?.let(DefaultBlockParameter::valueOf) ?: DefaultBlockParameterName.EARLIEST
        val endBlockParameter = DefaultBlockParameter.valueOf(endBlock.value)

        logger.debug { "Block range from: ${startBlockParameter.value} to: ${endBlockParameter.value}" }

        val accounts = HashSet<WalletAddress>()

        contract.transferEventFlowable(startBlockParameter, endBlockParameter).subscribe(
            { event ->
                accounts.add(WalletAddress(event.from))
                accounts.add(WalletAddress(event.to))
            },
            { error ->
                logger.error(error) { "Error processing contract transfer event" }
                throw InternalException(
                    ErrorCode.BLOCKCHAIN_CONTRACT_EVENT_READ_ERROR,
                    "Error processing contract transfer event"
                )
            }
        )

        logger.debug { "Found ${accounts.size} holder addresses for ERC20 contract: $erc20ContractAddress" }

        contract.setDefaultBlockParameter(endBlockParameter)

        return accounts.map { account ->
            val balance =
                contract.balanceOf(account.rawValue).sendSafely()?.let { Balance(it) } ?: throw InternalException(
                    ErrorCode.BLOCKCHAIN_CONTRACT_READ_ERROR, "Unable to fetch balance for address: $account"
                )
            AccountBalance(account, balance)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    fun <T> RemoteFunctionCall<T>.sendSafely(): T? {
        return try {
            this.send()
        } catch (ex: Exception) {
            logger.warn("Failed smart contract call", ex)
            null
        }
    }
}
