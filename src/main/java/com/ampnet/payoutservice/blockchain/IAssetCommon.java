package com.ampnet.payoutservice.blockchain;

import java.math.BigInteger;
import java.util.Arrays;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicStruct;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
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
public class IAssetCommon extends FixedContract {
    public static final String BINARY = "";

    public static final String FUNC_COMMONSTATE = "commonState";

    @Deprecated
    protected IAssetCommon(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected IAssetCommon(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected IAssetCommon(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected IAssetCommon(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public RemoteFunctionCall<AssetCommonState> commonState() {
        final Function function = new Function(FUNC_COMMONSTATE,
                Arrays.<Type>asList(),
                Arrays.<TypeReference<?>>asList(new TypeReference<AssetCommonState>() {}));
        return executeRemoteCallSingleValueReturn(function, AssetCommonState.class);
    }

    @Deprecated
    public static IAssetCommon load(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new IAssetCommon(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static IAssetCommon load(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new IAssetCommon(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static IAssetCommon load(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return new IAssetCommon(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static IAssetCommon load(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new IAssetCommon(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static RemoteCall<IAssetCommon> deploy(Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(IAssetCommon.class, web3j, credentials, contractGasProvider, BINARY, "");
    }

    @Deprecated
    public static RemoteCall<IAssetCommon> deploy(Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(IAssetCommon.class, web3j, credentials, gasPrice, gasLimit, BINARY, "");
    }

    public static RemoteCall<IAssetCommon> deploy(Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(IAssetCommon.class, web3j, transactionManager, contractGasProvider, BINARY, "");
    }

    @Deprecated
    public static RemoteCall<IAssetCommon> deploy(Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(IAssetCommon.class, web3j, transactionManager, gasPrice, gasLimit, BINARY, "");
    }

    public static class AssetCommonState extends DynamicStruct {
        public String flavor;

        public String version;

        public String contractAddress;

        public String owner;

        public String info;

        public String name;

        public String symbol;

        public BigInteger totalSupply;

        public BigInteger decimals;

        public String issuer;

        public AssetCommonState(String flavor, String version, String contractAddress, String owner, String info, String name, String symbol, BigInteger totalSupply, BigInteger decimals, String issuer) {
            super(new org.web3j.abi.datatypes.Utf8String(flavor),new org.web3j.abi.datatypes.Utf8String(version),new org.web3j.abi.datatypes.Address(contractAddress),new org.web3j.abi.datatypes.Address(owner),new org.web3j.abi.datatypes.Utf8String(info),new org.web3j.abi.datatypes.Utf8String(name),new org.web3j.abi.datatypes.Utf8String(symbol),new org.web3j.abi.datatypes.generated.Uint256(totalSupply),new org.web3j.abi.datatypes.generated.Uint8(decimals),new org.web3j.abi.datatypes.Address(issuer));
            this.flavor = flavor;
            this.version = version;
            this.contractAddress = contractAddress;
            this.owner = owner;
            this.info = info;
            this.name = name;
            this.symbol = symbol;
            this.totalSupply = totalSupply;
            this.decimals = decimals;
            this.issuer = issuer;
        }

        public AssetCommonState(Utf8String flavor, Utf8String version, Address contractAddress, Address owner, Utf8String info, Utf8String name, Utf8String symbol, Uint256 totalSupply, Uint8 decimals, Address issuer) {
            super(flavor,version,contractAddress,owner,info,name,symbol,totalSupply,decimals,issuer);
            this.flavor = flavor.getValue();
            this.version = version.getValue();
            this.contractAddress = contractAddress.getValue();
            this.owner = owner.getValue();
            this.info = info.getValue();
            this.name = name.getValue();
            this.symbol = symbol.getValue();
            this.totalSupply = totalSupply.getValue();
            this.decimals = decimals.getValue();
            this.issuer = issuer.getValue();
        }
    }
}
