package com.ampnet.payoutservice.blockchain;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.StaticStruct;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.tx.FixedContract;
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
public class IPayoutService extends FixedContract {
    public static final String BINARY = "";

    public static final String FUNC___DEF_PAYOUTSTATEFORINVESTOR_STRUCT__ = "__def_PayoutStateForInvestor_struct__";

    public static final String FUNC___DEF_PAYOUT_STRUCT__ = "__def_Payout_struct__";

    public static final String FUNC_GETPAYOUTSTATESFORINVESTOR = "getPayoutStatesForInvestor";

    public static final String FUNC_GETPAYOUTSFORISSUER = "getPayoutsForIssuer";

    @Deprecated
    protected IPayoutService(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected IPayoutService(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected IPayoutService(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected IPayoutService(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public RemoteFunctionCall<PayoutStateForInvestor> __def_PayoutStateForInvestor_struct__() {
        final Function function = new Function(FUNC___DEF_PAYOUTSTATEFORINVESTOR_STRUCT__, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<PayoutStateForInvestor>() {}));
        return executeRemoteCallSingleValueReturn(function, PayoutStateForInvestor.class);
    }

    public RemoteFunctionCall<PayoutStruct> __def_Payout_struct__() {
        final Function function = new Function(FUNC___DEF_PAYOUT_STRUCT__, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<PayoutStruct>() {}));
        return executeRemoteCallSingleValueReturn(function, PayoutStruct.class);
    }

    public RemoteFunctionCall<List<PayoutStateForInvestor>> getPayoutStatesForInvestor(String investor, String payoutManager, List<BigInteger> payoutIds) {
        final Function function = new Function(FUNC_GETPAYOUTSTATESFORINVESTOR, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, investor), 
                new org.web3j.abi.datatypes.Address(160, payoutManager), 
                new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.generated.Uint256>(
                        org.web3j.abi.datatypes.generated.Uint256.class,
                        org.web3j.abi.Utils.typeMap(payoutIds, org.web3j.abi.datatypes.generated.Uint256.class))), 
                Arrays.<TypeReference<?>>asList(new TypeReference<DynamicArray<PayoutStateForInvestor>>() {}));
        return new RemoteFunctionCall<List<PayoutStateForInvestor>>(function,
                new Callable<List<PayoutStateForInvestor>>() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public List<PayoutStateForInvestor> call() throws Exception {
                        List<Type> result = (List<Type>) executeCallSingleValueReturn(function, List.class);
                        return convertToNative(result);
                    }
                });
    }

    public RemoteFunctionCall<List<PayoutStruct>> getPayoutsForIssuer(String issuer, String payoutManager, List<String> assetFactories) {
        final Function function = new Function(FUNC_GETPAYOUTSFORISSUER, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, issuer), 
                new org.web3j.abi.datatypes.Address(160, payoutManager), 
                new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.Address>(
                        org.web3j.abi.datatypes.Address.class,
                        org.web3j.abi.Utils.typeMap(assetFactories, org.web3j.abi.datatypes.Address.class))), 
                Arrays.<TypeReference<?>>asList(new TypeReference<DynamicArray<PayoutStruct>>() {}));
        return new RemoteFunctionCall<List<PayoutStruct>>(function,
                new Callable<List<PayoutStruct>>() {
                    @Override
                    @SuppressWarnings("unchecked")
                    public List<PayoutStruct> call() throws Exception {
                        List<Type> result = (List<Type>) executeCallSingleValueReturn(function, List.class);
                        return convertToNative(result);
                    }
                });
    }

    @Deprecated
    public static IPayoutService load(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new IPayoutService(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static IPayoutService load(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new IPayoutService(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static IPayoutService load(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return new IPayoutService(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static IPayoutService load(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new IPayoutService(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static RemoteCall<IPayoutService> deploy(Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(IPayoutService.class, web3j, credentials, contractGasProvider, BINARY, "");
    }

    @Deprecated
    public static RemoteCall<IPayoutService> deploy(Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(IPayoutService.class, web3j, credentials, gasPrice, gasLimit, BINARY, "");
    }

    public static RemoteCall<IPayoutService> deploy(Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(IPayoutService.class, web3j, transactionManager, contractGasProvider, BINARY, "");
    }

    @Deprecated
    public static RemoteCall<IPayoutService> deploy(Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(IPayoutService.class, web3j, transactionManager, gasPrice, gasLimit, BINARY, "");
    }

    public static class PayoutStateForInvestor extends StaticStruct {
        public BigInteger payoutId;

        public String investor;

        public BigInteger amountClaimed;

        public PayoutStateForInvestor(BigInteger payoutId, String investor, BigInteger amountClaimed) {
            super(new org.web3j.abi.datatypes.generated.Uint256(payoutId),new org.web3j.abi.datatypes.Address(investor),new org.web3j.abi.datatypes.generated.Uint256(amountClaimed));
            this.payoutId = payoutId;
            this.investor = investor;
            this.amountClaimed = amountClaimed;
        }

        public PayoutStateForInvestor(Uint256 payoutId, Address investor, Uint256 amountClaimed) {
            super(payoutId,investor,amountClaimed);
            this.payoutId = payoutId.getValue();
            this.investor = investor.getValue();
            this.amountClaimed = amountClaimed.getValue();
        }
    }
}
