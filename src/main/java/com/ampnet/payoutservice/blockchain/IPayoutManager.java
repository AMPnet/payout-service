package com.ampnet.payoutservice.blockchain;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.tx.Contract;
import org.web3j.tx.TransactionManager;
import org.web3j.tx.gas.ContractGasProvider;

/**
 * <p>Auto generated code.
 * <p><strong>Do not modify!</strong>
 * <p>Please use the <a href="https://docs.web3j.io/command_line.html">web3j command line tools</a>,
 * or the org.web3j.codegen.SolidityFunctionWrapperGenerator in the 
 * <a href="https://github.com/web3j/web3j/tree/master/codegen">codegen module</a> to update.
 *
 * <p>Generated with web3j version 1.4.1.
 */
@SuppressWarnings("rawtypes")
public class IPayoutManager extends Contract {
    public static final String BINARY = "";

    public static final String FUNC_GETAMOUNTOFCLAIMEDFUNDS = "getAmountOfClaimedFunds";

    public static final String FUNC_GETCURRENTPAYOUTID = "getCurrentPayoutId";

    public static final String FUNC_GETPAYOUTIDSFORASSET = "getPayoutIdsForAsset";

    public static final String FUNC_GETPAYOUTIDSFOROWNER = "getPayoutIdsForOwner";

    public static final String FUNC_GETPAYOUTINFO = "getPayoutInfo";

    public static final String FUNC_GETPAYOUTSFORASSET = "getPayoutsForAsset";

    public static final String FUNC_GETPAYOUTSFOROWNER = "getPayoutsForOwner";

    @Deprecated
    protected IPayoutManager(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected IPayoutManager(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected IPayoutManager(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected IPayoutManager(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public RemoteFunctionCall<BigInteger> getAmountOfClaimedFunds(BigInteger _payoutId, String _wallet) {
        final Function function = new Function(FUNC_GETAMOUNTOFCLAIMEDFUNDS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(_payoutId), 
                new org.web3j.abi.datatypes.Address(160, _wallet)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<BigInteger> getCurrentPayoutId() {
        final Function function = new Function(FUNC_GETCURRENTPAYOUTID, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<List> getPayoutIdsForAsset(String _assetAddress) {
        final Function function = new Function(FUNC_GETPAYOUTIDSFORASSET, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _assetAddress)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<DynamicArray<Uint256>>() {}));
        return new RemoteFunctionCall<List>(function,
                new Callable<List>() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public List call() throws Exception {
                        List<Type> result = (List<Type>) executeCallSingleValueReturn(function, List.class);
                        return convertToNative(result);
                    }
                });
    }

    public RemoteFunctionCall<List> getPayoutIdsForOwner(String _ownerAddress) {
        final Function function = new Function(FUNC_GETPAYOUTIDSFOROWNER, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _ownerAddress)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<DynamicArray<Uint256>>() {}));
        return new RemoteFunctionCall<List>(function,
                new Callable<List>() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public List call() throws Exception {
                        List<Type> result = (List<Type>) executeCallSingleValueReturn(function, List.class);
                        return convertToNative(result);
                    }
                });
    }

    public RemoteFunctionCall<Payout> getPayoutInfo(BigInteger _payoutId) {
        final Function function = new Function(FUNC_GETPAYOUTINFO, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Uint256(_payoutId)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Payout>() {}));
        return executeRemoteCallSingleValueReturn(function, Payout.class);
    }

    public RemoteFunctionCall<List> getPayoutsForAsset(String _assetAddress) {
        final Function function = new Function(FUNC_GETPAYOUTSFORASSET, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _assetAddress)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<DynamicArray<Payout>>() {}));
        return new RemoteFunctionCall<List>(function,
                new Callable<List>() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public List call() throws Exception {
                        List<Type> result = (List<Type>) executeCallSingleValueReturn(function, List.class);
                        return convertToNative(result);
                    }
                });
    }

    public RemoteFunctionCall<List> getPayoutsForOwner(String _ownerAddress) {
        final Function function = new Function(FUNC_GETPAYOUTSFOROWNER, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, _ownerAddress)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<DynamicArray<Payout>>() {}));
        return new RemoteFunctionCall<List>(function,
                new Callable<List>() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public List call() throws Exception {
                        List<Type> result = (List<Type>) executeCallSingleValueReturn(function, List.class);
                        return convertToNative(result);
                    }
                });
    }

    @Deprecated
    public static IPayoutManager load(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new IPayoutManager(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static IPayoutManager load(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new IPayoutManager(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static IPayoutManager load(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return new IPayoutManager(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static IPayoutManager load(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new IPayoutManager(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static RemoteCall<IPayoutManager> deploy(Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(IPayoutManager.class, web3j, credentials, contractGasProvider, BINARY, "");
    }

    @Deprecated
    public static RemoteCall<IPayoutManager> deploy(Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(IPayoutManager.class, web3j, credentials, gasPrice, gasLimit, BINARY, "");
    }

    public static RemoteCall<IPayoutManager> deploy(Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(IPayoutManager.class, web3j, transactionManager, contractGasProvider, BINARY, "");
    }

    @Deprecated
    public static RemoteCall<IPayoutManager> deploy(Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(IPayoutManager.class, web3j, transactionManager, gasPrice, gasLimit, BINARY, "");
    }
}
