package com.ampnet.payoutservice.blockchain

import com.ampnet.payoutservice.blockchain.IPayoutService.PayoutStateForInvestor
import com.ampnet.payoutservice.blockchain.properties.ChainPropertiesHandler
import com.ampnet.payoutservice.config.ApplicationProperties
import com.ampnet.payoutservice.exception.ErrorCode
import com.ampnet.payoutservice.exception.InternalException
import com.ampnet.payoutservice.model.params.GetIssuerPayoutsParams
import com.ampnet.payoutservice.model.params.GetPayoutsForAdminParams
import com.ampnet.payoutservice.model.params.GetPayoutsForInvestorParams
import com.ampnet.payoutservice.model.params.LoadPayoutManagerAndServiceParams
import com.ampnet.payoutservice.model.result.Payout
import com.ampnet.payoutservice.model.result.PayoutForInvestor
import com.ampnet.payoutservice.util.AccountBalance
import com.ampnet.payoutservice.util.Balance
import com.ampnet.payoutservice.util.BinarySearch
import com.ampnet.payoutservice.util.BlockNumber
import com.ampnet.payoutservice.util.ChainId
import com.ampnet.payoutservice.util.ContractAddress
import com.ampnet.payoutservice.util.WalletAddress
import mu.KLogging
import org.springframework.stereotype.Service
import org.web3j.protocol.core.DefaultBlockParameter
import org.web3j.protocol.core.DefaultBlockParameterName
import org.web3j.protocol.core.RemoteFunctionCall
import org.web3j.protocol.core.Request
import org.web3j.protocol.core.Response
import org.web3j.tx.ReadonlyTransactionManager
import org.web3j.tx.gas.DefaultGasProvider
import java.math.BigInteger

@Service
@Suppress("TooManyFunctions")
class Web3jBlockchainService(applicationProperties: ApplicationProperties) : BlockchainService {

    companion object : KLogging()

    private val chainHandler = ChainPropertiesHandler(applicationProperties)

    @Throws(InternalException::class)
    override fun fetchErc20AccountBalances(
        chainId: ChainId,
        erc20ContractAddress: ContractAddress,
        ignoredErc20Addresses: Set<WalletAddress>,
        startBlock: BlockNumber?,
        endBlock: BlockNumber
    ): List<AccountBalance> {
        logger.info {
            "Fetching balances for ERC20 contract: $erc20ContractAddress on chain: $chainId," +
                " ignored addresses: $ignoredErc20Addresses"
        }
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

        // TODO split this into 2k blocks for larger assets - TBD on sprint planning
        val accounts = contract.findAccounts(startBlockParameter, endBlockParameter) - ignoredErc20Addresses

        logger.debug { "Found ${accounts.size} holder addresses for ERC20 contract: $erc20ContractAddress" }

        contract.setDefaultBlockParameter(endBlockParameter)

        return accounts.map { account ->
            val balance =
                contract.balanceOf(account.rawValue).sendSafely()?.let { Balance(it) } ?: throw InternalException(
                    ErrorCode.BLOCKCHAIN_CONTRACT_READ_ERROR, "Unable to fetch balance for address: $account"
                )
            AccountBalance(account, balance)
        }.filter { it.balance.rawValue > BigInteger.ZERO }
    }

    @Throws(InternalException::class)
    override fun getAssetOwner(chainId: ChainId, assetAddress: ContractAddress): WalletAddress {
        logger.debug { "Get owner of asset: $assetAddress on chain: $chainId" }
        val blockchainProperties = chainHandler.getBlockchainProperties(chainId)
        val contract = IAssetCommon.load(
            assetAddress.rawValue,
            blockchainProperties.web3j,
            ReadonlyTransactionManager(blockchainProperties.web3j, assetAddress.rawValue),
            DefaultGasProvider()
        )

        return contract.commonState().sendSafely()?.owner?.let { WalletAddress(it) }
            ?: throw InternalException(
                ErrorCode.BLOCKCHAIN_CONTRACT_READ_ERROR,
                "Failed to fetch asset owner address for contract address: $assetAddress"
            )
    }

    @Throws(InternalException::class)
    override fun getPayoutsForAdmin(params: GetPayoutsForAdminParams): List<Payout> {
        logger.debug { "Get payouts for admin, params: $params" }
        val (manager, service) = loadPayoutManagerAndService(params)

        val payouts = if (params.issuer == null && params.owner == null) {
            manager.fetchAllPayouts()
        } else if (params.issuer == null) { // implies params.owner != null
            manager.fetchAllPayoutsForOwner(params)
        } else if (params.owner == null) { // implies params.issuer != null
            service.fetchAllPayoutsForIssuer(params)
        } else { // implies params.issuer != null && params.owner != null
            service.fetchAllPayoutsForIssuer(params)?.filter { WalletAddress(it.payoutOwner) == params.owner }
        }

        return payouts?.map { Payout(it) } ?: throw InternalException(
            ErrorCode.BLOCKCHAIN_CONTRACT_READ_ERROR,
            "Failed reading payout data for admin"
        )
    }

    @Throws(InternalException::class)
    override fun getPayoutsForInvestor(params: GetPayoutsForInvestorParams): List<PayoutForInvestor> {
        logger.debug { "Get payouts for investor, params: $params" }
        val (manager, service) = loadPayoutManagerAndService(params)

        val payoutStates = if (params.issuer == null) {
            manager.fetchAllPayouts()?.let { allPayouts ->
                manager.fetchAllPayoutStatesForInvestor(params, allPayouts)
            }
        } else {
            service.fetchAllPayoutsForIssuer(params)?.let { issuerPayouts ->
                service.fetchAllPayoutStatesForInvestorByIssuer(params, issuerPayouts)
            }
        }

        return payoutStates?.map { PayoutForInvestor(it.first, it.second) } ?: throw InternalException(
            ErrorCode.BLOCKCHAIN_CONTRACT_READ_ERROR,
            "Failed reading payout data for investor"
        )
    }

    @Throws(InternalException::class)
    override fun findContractDeploymentBlockNumber(chainId: ChainId, contractAddress: ContractAddress): BlockNumber {
        val blockchainProperties = chainHandler.getBlockchainProperties(chainId)

        return BinarySearch(
            lowerBound = BigInteger.ZERO,
            upperBound = blockchainProperties.web3j.ethBlockNumber()
                .trySend("Failed RPC call: ethBlockNumber()").blockNumber,
            getValue = { currentBlock ->
                blockchainProperties.web3j.ethGetTransactionCount(
                    contractAddress.rawValue,
                    DefaultBlockParameter.valueOf(currentBlock)
                ).trySend("Failed RPC call: ethGetTransactionCount($contractAddress, $currentBlock)").transactionCount
            },
            updateLowerBound = { txCount -> txCount == BigInteger.ZERO },
            updateUpperBound = { txCount -> txCount != BigInteger.ZERO }
        ).let { BlockNumber(it) }
    }

    private fun IERC20.findAccounts(
        startBlockParameter: DefaultBlockParameter,
        endBlockParameter: DefaultBlockParameter
    ): Set<WalletAddress> {
        val accounts = HashSet<WalletAddress>()
        val errors = mutableListOf<InternalException>()

        transferEventFlowable(startBlockParameter, endBlockParameter)
            .subscribe(
                { event ->
                    accounts.add(WalletAddress(event.from))
                    accounts.add(WalletAddress(event.to))
                },
                { error ->
                    logger.error(error) { "Error processing contract transfer event" }
                    errors += InternalException(
                        ErrorCode.BLOCKCHAIN_CONTRACT_EVENT_READ_ERROR,
                        "Error processing contract transfer event",
                        error
                    )
                }
            ).dispose()

        if (errors.isNotEmpty()) {
            throw errors[0]
        }

        return accounts
    }

    private fun loadPayoutManagerAndService(
        params: LoadPayoutManagerAndServiceParams
    ): Pair<IPayoutManager, IPayoutService> {
        val blockchainProperties = chainHandler.getBlockchainProperties(params.chainId)
        val service = IPayoutService.load(
            params.payoutService.rawValue,
            blockchainProperties.web3j,
            ReadonlyTransactionManager(blockchainProperties.web3j, params.payoutService.rawValue),
            DefaultGasProvider()
        )
        val manager = IPayoutManager.load(
            params.payoutManager.rawValue,
            blockchainProperties.web3j,
            ReadonlyTransactionManager(blockchainProperties.web3j, params.payoutManager.rawValue),
            DefaultGasProvider()
        )

        return Pair(manager, service)
    }

    private fun IPayoutService.fetchAllPayoutsForIssuer(params: GetIssuerPayoutsParams): List<PayoutStruct>? =
        getPayoutsForIssuer(
            params.issuer?.rawValue,
            params.payoutManager.rawValue,
            params.assetFactories.map { it.rawValue }
        ).sendSafely()

    private fun IPayoutService.fetchAllPayoutStatesForInvestorByIssuer(
        params: GetPayoutsForInvestorParams,
        issuerPayouts: List<PayoutStruct>
    ): List<Pair<PayoutStruct, PayoutStateForInvestor>>? {
        val payoutIds = issuerPayouts.map { it.payoutId }
        val investorPayoutStates = getPayoutStatesForInvestor(
            params.investor.rawValue,
            params.payoutManager.rawValue,
            payoutIds
        )
            .sendSafely()

        val payoutsById = issuerPayouts.associateBy { it.payoutId }

        return investorPayoutStates?.map { Pair(payoutsById.getValue(it.payoutId), it) }
    }

    private fun IPayoutManager.fetchAllPayoutsForOwner(params: GetPayoutsForAdminParams): List<PayoutStruct>? =
        getPayoutsForOwner(params.owner?.rawValue).sendSafely()

    @Suppress("TooGenericExceptionCaught")
    private fun IPayoutManager.fetchAllPayouts(): List<PayoutStruct>? =
        try {
            val numOfPayouts = currentPayoutId.send().longValueExact()
            (0L until numOfPayouts).map { id -> getPayoutInfo(BigInteger.valueOf(id)).send() }
        } catch (ex: Exception) {
            logger.warn("Failed smart contract call", ex)
            null
        }

    @Suppress("TooGenericExceptionCaught")
    private fun IPayoutManager.fetchAllPayoutStatesForInvestor(
        params: GetPayoutsForInvestorParams,
        allPayouts: List<PayoutStruct>
    ): List<Pair<PayoutStruct, PayoutStateForInvestor>>? =
        try {
            val payoutIds = allPayouts.map { it.payoutId }
            val claimedFunds = payoutIds.associate { payoutId ->
                Pair(
                    payoutId,
                    Balance(getAmountOfClaimedFunds(payoutId, params.investor.rawValue).send())
                )
            }

            allPayouts.map { payout ->
                Pair(
                    payout,
                    PayoutStateForInvestor(
                        payout.payoutId,
                        params.investor.rawValue,
                        claimedFunds.getValue(payout.payoutId).rawValue
                    )
                )
            }
        } catch (ex: Exception) {
            logger.warn("Failed smart contract call", ex)
            null
        }

    @Suppress("TooGenericExceptionCaught")
    private fun <T> RemoteFunctionCall<T>.sendSafely(): T? =
        try {
            this.send()
        } catch (ex: Exception) {
            logger.warn("Failed smart contract call", ex)
            null
        }

    @Suppress("TooGenericExceptionCaught")
    private fun <T : Response<*>> Request<*, T>.trySend(errorMessage: String): T =
        try {
            this.send()
        } catch (ex: Exception) {
            logger.error("Failed RPC call", ex)
            throw InternalException(ErrorCode.BLOCKCHAIN_READ_ERROR, errorMessage)
        }
}
