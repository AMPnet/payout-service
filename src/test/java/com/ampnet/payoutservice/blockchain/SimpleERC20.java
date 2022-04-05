package com.ampnet.payoutservice.blockchain;

import io.reactivex.Flowable;
import io.reactivex.functions.Function;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.DynamicStruct;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.abi.datatypes.generated.Uint8;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.RemoteCall;
import org.web3j.protocol.core.RemoteFunctionCall;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.BaseEventResponse;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
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
public class SimpleERC20 extends FixedContract {
    public static final String BINARY = "608060405234801561001057600080fd5b506040516108f03803806108f083398101604081905261002f916101e4565b815183511461009a5760405162461bcd60e51b815260206004820152602d60248201527f4d69736d61746368696e67206163636f756e7420616e642062616c616e63652060448201526c6172726179206c656e6774687360981b606482015260840160405180910390fd5b60005b8351811015610130578281815181106100c657634e487b7160e01b600052603260045260246000fd5b60200260200101516000808684815181106100f157634e487b7160e01b600052603260045260246000fd5b60200260200101516001600160a01b03166001600160a01b031681526020019081526020016000208190555080806101289061030a565b91505061009d565b50600180546001600160a01b0319166001600160a01b0392909216919091179055506103479050565b80516001600160a01b038116811461017057600080fd5b919050565b600082601f830112610185578081fd5b8151602061019a610195836102e7565b6102b7565b80838252828201915082860187848660051b89010111156101b9578586fd5b855b858110156101d7578151845292840192908401906001016101bb565b5090979650505050505050565b6000806000606084860312156101f8578283fd5b83516001600160401b038082111561020e578485fd5b818601915086601f830112610221578485fd5b81516020610231610195836102e7565b8083825282820191508286018b848660051b890101111561025057898afd5b8996505b848710156102795761026581610159565b835260019690960195918301918301610254565b5091890151919750909350505080821115610292578384fd5b5061029f86828701610175565b9250506102ae60408501610159565b90509250925092565b604051601f8201601f191681016001600160401b03811182821017156102df576102df610331565b604052919050565b60006001600160401b0382111561030057610300610331565b5060051b60200190565b600060001982141561032a57634e487b7160e01b81526011600452602481fd5b5060010190565b634e487b7160e01b600052604160045260246000fd5b61059a806103566000396000f3fe608060405234801561001057600080fd5b50600436106100415760003560e01c80631818e2ec1461004657806370a0823114610064578063a9059cbb1461009b575b600080fd5b61004e6100be565b60405161005b9190610417565b60405180910390f35b61008d610072366004610382565b6001600160a01b031660009081526020819052604090205490565b60405190815260200161005b565b6100ae6100a93660046103a3565b610210565b604051901515815260200161005b565b610132604051806101400160405280606081526020016060815260200160006001600160a01b0316815260200160006001600160a01b0316815260200160608152602001606081526020016060815260200160008152602001600060ff16815260200160006001600160a01b031681525090565b5060408051610180810182526006610140820181815265333630bb37b960d11b61016084015282528251808401845260078152663b32b939b4b7b760c91b6020828101919091528084019190915260008385018190526001546001600160a01b0316606085015284518086018652600480825263696e666f60e01b82850152608086019190915285518087018752908152636e616d6560e01b8184015260a08501528451808601909552918452651cde5b589bdb60d21b9084015260c082019290925260e08101829052610100810182905261012081019190915290565b60006001600160a01b03831661026d5760405162461bcd60e51b815260206004820152601c60248201527f5472616e7366657220746f20746865207a65726f20616464726573730000000060448201526064015b60405180910390fd5b336000908152602081905260409020548211156102cc5760405162461bcd60e51b815260206004820152601f60248201527f5472616e7366657220616d6f756e7420657863656564732062616c616e6365006044820152606401610264565b33600090815260208190526040812080548492906102eb908490610537565b90915550506001600160a01b0383166000908152602081905260408120805484929061031890849061051f565b90915550506040518281526001600160a01b0384169033907fddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef9060200160405180910390a350600192915050565b80356001600160a01b038116811461037d57600080fd5b919050565b600060208284031215610393578081fd5b61039c82610366565b9392505050565b600080604083850312156103b5578081fd5b6103be83610366565b946020939093013593505050565b60008151808452815b818110156103f1576020818501810151868301820152016103d5565b818111156104025782602083870101525b50601f01601f19169290920160200192915050565b60208152600082516101408060208501526104366101608501836103cc565b91506020850151601f198086850301604087015261045484836103cc565b93506040870151915061047260608701836001600160a01b03169052565b60608701516001600160a01b0381166080880152915060808701519150808685030160a08701526104a384836103cc565b935060a08701519150808685030160c08701526104c084836103cc565b935060c08701519150808685030160e0870152506104de83826103cc565b92505060e08501516101008181870152808701519150506101206105068187018360ff169052565b909501516001600160a01b031693019290925250919050565b600082198211156105325761053261054e565b500190565b6000828210156105495761054961054e565b500390565b634e487b7160e01b600052601160045260246000fdfea264697066735822122074da4fa560ced7d06d8de9f4b0e5b5f6fa47b0a151f2351d5e7b9358a9686ffe64736f6c63430008040033";

    public static final String FUNC_BALANCEOF = "balanceOf";

    public static final String FUNC_COMMONSTATE = "commonState";

    public static final String FUNC_TRANSFER = "transfer";

    public static final Event TRANSFER_EVENT = new Event("Transfer",
            Arrays.<TypeReference<?>>asList(new TypeReference<Address>(true) {}, new TypeReference<Address>(true) {}, new TypeReference<Uint256>() {}));
    ;

    @Deprecated
    protected SimpleERC20(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected SimpleERC20(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected SimpleERC20(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected SimpleERC20(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public List<TransferEventResponse> getTransferEvents(TransactionReceipt transactionReceipt) {
        List<FixedContract.EventValuesWithLog> valueList = extractEventParametersWithLog(TRANSFER_EVENT, transactionReceipt);
        ArrayList<TransferEventResponse> responses = new ArrayList<TransferEventResponse>(valueList.size());
        for (FixedContract.EventValuesWithLog eventValues : valueList) {
            TransferEventResponse typedResponse = new TransferEventResponse();
            typedResponse.log = eventValues.getLog();
            typedResponse.from = (String) eventValues.getIndexedValues().get(0).getValue();
            typedResponse.to = (String) eventValues.getIndexedValues().get(1).getValue();
            typedResponse.value = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
            responses.add(typedResponse);
        }
        return responses;
    }

    public Flowable<TransferEventResponse> transferEventFlowable(EthFilter filter) {
        return web3j.ethLogFlowable(filter).map(new Function<Log, TransferEventResponse>() {
            @Override
            public TransferEventResponse apply(Log log) {
                FixedContract.EventValuesWithLog eventValues = extractEventParametersWithLog(TRANSFER_EVENT, log);
                TransferEventResponse typedResponse = new TransferEventResponse();
                typedResponse.log = log;
                typedResponse.from = (String) eventValues.getIndexedValues().get(0).getValue();
                typedResponse.to = (String) eventValues.getIndexedValues().get(1).getValue();
                typedResponse.value = (BigInteger) eventValues.getNonIndexedValues().get(0).getValue();
                return typedResponse;
            }
        });
    }

    public Flowable<TransferEventResponse> transferEventFlowable(DefaultBlockParameter startBlock, DefaultBlockParameter endBlock) {
        EthFilter filter = new EthFilter(startBlock, endBlock, getContractAddress());
        filter.addSingleTopic(EventEncoder.encode(TRANSFER_EVENT));
        return transferEventFlowable(filter);
    }

    public RemoteFunctionCall<BigInteger> balanceOf(String account) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_BALANCEOF,
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, account)),
                Arrays.<TypeReference<?>>asList(new TypeReference<Uint256>() {}));
        return executeRemoteCallSingleValueReturn(function, BigInteger.class);
    }

    public RemoteFunctionCall<AssetCommonState> commonState() {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(FUNC_COMMONSTATE,
                Arrays.<Type>asList(),
                Arrays.<TypeReference<?>>asList(new TypeReference<AssetCommonState>() {}));
        return executeRemoteCallSingleValueReturn(function, AssetCommonState.class);
    }

    public RemoteFunctionCall<TransactionReceipt> transfer(String recipient, BigInteger amount) {
        final org.web3j.abi.datatypes.Function function = new org.web3j.abi.datatypes.Function(
                FUNC_TRANSFER,
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, recipient),
                        new org.web3j.abi.datatypes.generated.Uint256(amount)),
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
    }

    @Deprecated
    public static SimpleERC20 load(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new SimpleERC20(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static SimpleERC20 load(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new SimpleERC20(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static SimpleERC20 load(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return new SimpleERC20(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static SimpleERC20 load(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new SimpleERC20(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static RemoteCall<SimpleERC20> deploy(Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider, List<String> accounts, List<BigInteger> balances, String ownerAddress) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.Address>(
                        org.web3j.abi.datatypes.Address.class,
                        org.web3j.abi.Utils.typeMap(accounts, org.web3j.abi.datatypes.Address.class)),
                new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.generated.Uint256>(
                        org.web3j.abi.datatypes.generated.Uint256.class,
                        org.web3j.abi.Utils.typeMap(balances, org.web3j.abi.datatypes.generated.Uint256.class)),
                new org.web3j.abi.datatypes.Address(160, ownerAddress)));
        return deployRemoteCall(SimpleERC20.class, web3j, credentials, contractGasProvider, BINARY, encodedConstructor);
    }

    public static RemoteCall<SimpleERC20> deploy(Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider, List<String> accounts, List<BigInteger> balances, String ownerAddress) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.Address>(
                        org.web3j.abi.datatypes.Address.class,
                        org.web3j.abi.Utils.typeMap(accounts, org.web3j.abi.datatypes.Address.class)),
                new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.generated.Uint256>(
                        org.web3j.abi.datatypes.generated.Uint256.class,
                        org.web3j.abi.Utils.typeMap(balances, org.web3j.abi.datatypes.generated.Uint256.class)),
                new org.web3j.abi.datatypes.Address(160, ownerAddress)));
        return deployRemoteCall(SimpleERC20.class, web3j, transactionManager, contractGasProvider, BINARY, encodedConstructor);
    }

    @Deprecated
    public static RemoteCall<SimpleERC20> deploy(Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit, List<String> accounts, List<BigInteger> balances, String ownerAddress) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.Address>(
                        org.web3j.abi.datatypes.Address.class,
                        org.web3j.abi.Utils.typeMap(accounts, org.web3j.abi.datatypes.Address.class)),
                new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.generated.Uint256>(
                        org.web3j.abi.datatypes.generated.Uint256.class,
                        org.web3j.abi.Utils.typeMap(balances, org.web3j.abi.datatypes.generated.Uint256.class)),
                new org.web3j.abi.datatypes.Address(160, ownerAddress)));
        return deployRemoteCall(SimpleERC20.class, web3j, credentials, gasPrice, gasLimit, BINARY, encodedConstructor);
    }

    @Deprecated
    public static RemoteCall<SimpleERC20> deploy(Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit, List<String> accounts, List<BigInteger> balances, String ownerAddress) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.Address>(
                        org.web3j.abi.datatypes.Address.class,
                        org.web3j.abi.Utils.typeMap(accounts, org.web3j.abi.datatypes.Address.class)),
                new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.generated.Uint256>(
                        org.web3j.abi.datatypes.generated.Uint256.class,
                        org.web3j.abi.Utils.typeMap(balances, org.web3j.abi.datatypes.generated.Uint256.class)),
                new org.web3j.abi.datatypes.Address(160, ownerAddress)));
        return deployRemoteCall(SimpleERC20.class, web3j, transactionManager, gasPrice, gasLimit, BINARY, encodedConstructor);
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

    public static class TransferEventResponse extends BaseEventResponse {
        public String from;

        public String to;

        public BigInteger value;
    }
}
