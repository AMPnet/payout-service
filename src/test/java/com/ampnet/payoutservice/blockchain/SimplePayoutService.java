package com.ampnet.payoutservice.blockchain;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
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
public class SimplePayoutService extends FixedContract {
    public static final String BINARY = "608060405234801561001057600080fd5b50610dca806100206000396000f3fe608060405234801561001057600080fd5b506004361061004c5760003560e01c806305848f5b1461005157806379a57164146100665780638f6c8b131461008f57806391ce6b28146100af575b600080fd5b61006461005f3660046108c0565b6100e2565b005b610079610074366004610860565b61010e565b6040516100869190610adc565b60405180910390f35b6100a261009d36600461079d565b6102ef565b6040516100869190610b47565b6100b76104fe565b60408051825181526020808401516001600160a01b0316908201529181015190820152606001610086565b6001600160a01b038216600090815260208181526040909120825161010992840190610532565b505050565b805160609080610152576040805160008082526020820190925290610149565b61013661057d565b81526020019060019003908161012e5790505b509150506102e8565b60008167ffffffffffffffff81111561017b57634e487b7160e01b600052604160045260246000fd5b6040519080825280602002602001820160405280156101b457816020015b6101a161057d565b8152602001906001900390816101995790505b5090508460005b838110156102e15760008682815181106101e557634e487b7160e01b600052603260045260246000fd5b6020026020010151905060405180606001604052808281526020018a6001600160a01b03168152602001846001600160a01b0316635fe9c20c848d6040518363ffffffff1660e01b815260040161024f9291909182526001600160a01b0316602082015260400190565b60206040518083038186803b15801561026757600080fd5b505afa15801561027b573d6000803e3d6000fd5b505050506040513d601f19601f8201168201806040525081019061029f9190610a55565b8152508483815181106102c257634e487b7160e01b600052603260045260246000fd5b60200260200101819052505080806102d990610d3f565b9150506101bb565b5090925050505b9392505050565b6001600160a01b03831660009081526020818152604080832080548251818502810185019093528083526060949383018282801561034c57602002820191906000526020600020905b815481526020019060010190808311610338575b505083519394505050811515905061039957604080516000808252602082019092529061038f565b61037c6105a7565b8152602001906001900390816103745790505b50925050506102e8565b60008167ffffffffffffffff8111156103c257634e487b7160e01b600052604160045260246000fd5b6040519080825280602002602001820160405280156103fb57816020015b6103e86105a7565b8152602001906001900390816103e05790505b5090508560005b838110156104f157816001600160a01b0316637a5c15c886838151811061043957634e487b7160e01b600052603260045260246000fd5b60200260200101516040518263ffffffff1660e01b815260040161045f91815260200190565b60006040518083038186803b15801561047757600080fd5b505afa15801561048b573d6000803e3d6000fd5b505050506040513d6000823e601f3d908101601f191682016040526104b3919081019061090e565b8382815181106104d357634e487b7160e01b600052603260045260246000fd5b602002602001018190525080806104e990610d3f565b915050610402565b5090979650505050505050565b61050661057d565b60405180606001604052806000815260200160006001600160a01b031681526020016000815250905090565b82805482825590600052602060002090810192821561056d579160200282015b8281111561056d578251825591602001919060010190610552565b50610579929150610636565b5090565b60405180606001604052806000815260200160006001600160a01b03168152602001600081525090565b604051806101c001604052806000815260200160006001600160a01b031681526020016060815260200160001515815260200160006001600160a01b0316815260200160008152602001606081526020016000801916815260200160008152602001600081526020016060815260200160006001600160a01b0316815260200160008152602001600081525090565b5b808211156105795760008155600101610637565b805161065681610d7c565b919050565b600082601f83011261066b578081fd5b8151602061068061067b83610ceb565b610cba565b80838252828201915082860187848660051b890101111561069f578586fd5b855b858110156104f15781516106b481610d7c565b845292840192908401906001016106a1565b600082601f8301126106d6578081fd5b813560206106e661067b83610ceb565b80838252828201915082860187848660051b8901011115610705578586fd5b855b858110156104f157813584529284019290840190600101610707565b8051801515811461065657600080fd5b600082601f830112610743578081fd5b815167ffffffffffffffff81111561075d5761075d610d66565b610770601f8201601f1916602001610cba565b818152846020838601011115610784578283fd5b610795826020830160208701610d0f565b949350505050565b6000806000606084860312156107b1578283fd5b83356107bc81610d7c565b92506020848101356107cd81610d7c565b9250604085013567ffffffffffffffff8111156107e8578283fd5b8501601f810187136107f8578283fd5b803561080661067b82610ceb565b8082825284820191508484018a868560051b8701011115610825578687fd5b8694505b8385101561085057803561083c81610d7c565b835260019490940193918501918501610829565b5080955050505050509250925092565b600080600060608486031215610874578283fd5b833561087f81610d7c565b9250602084013561088f81610d7c565b9150604084013567ffffffffffffffff8111156108aa578182fd5b6108b6868287016106c6565b9150509250925092565b600080604083850312156108d2578182fd5b82356108dd81610d7c565b9150602083013567ffffffffffffffff8111156108f8578182fd5b610904858286016106c6565b9150509250929050565b60006020828403121561091f578081fd5b815167ffffffffffffffff80821115610936578283fd5b908301906101c0828603121561094a578283fd5b610952610c90565b825181526109626020840161064b565b6020820152604083015182811115610978578485fd5b61098487828601610733565b60408301525061099660608401610723565b60608201526109a76080840161064b565b608082015260a083015160a082015260c0830151828111156109c7578485fd5b6109d38782860161065b565b60c08301525060e08381015190820152610100808401519082015261012080840151908201526101408084015183811115610a0c578586fd5b610a1888828701610733565b8284015250506101609150610a2e82840161064b565b9181019190915261018082810151908201526101a091820151918101919091529392505050565b600060208284031215610a66578081fd5b5051919050565b6000815180845260208085019450808401835b83811015610aa55781516001600160a01b031687529582019590820190600101610a80565b509495945050505050565b60008151808452610ac8816020860160208601610d0f565b601f01601f19169290920160200192915050565b6020808252825182820181905260009190848201906040850190845b81811015610b3b57610b28838551805182526020808201516001600160a01b031690830152604090810151910152565b9284019260609290920191600101610af8565b50909695505050505050565b60006020808301818452808551808352604092508286019150828160051b870101848801865b83811015610c8257603f19898403018552815180518452878101516001600160a01b031688850152868101516101c088860181905290610baf82870182610ab0565b915050606080830151610bc58288018215159052565b50506080828101516001600160a01b03169086015260a0808301519086015260c08083015186830382880152610bfb8382610a6d565b9250505060e08083015181870152506101008083015181870152506101208083015181870152506101408083015186830382880152610c3a8382610ab0565b9250505061016080830151610c59828801826001600160a01b03169052565b505061018082810151908601526101a09182015191909401529386019390860190600101610b6d565b509098975050505050505050565b6040516101c0810167ffffffffffffffff81118282101715610cb457610cb4610d66565b60405290565b604051601f8201601f1916810167ffffffffffffffff81118282101715610ce357610ce3610d66565b604052919050565b600067ffffffffffffffff821115610d0557610d05610d66565b5060051b60200190565b60005b83811015610d2a578181015183820152602001610d12565b83811115610d39576000848401525b50505050565b6000600019821415610d5f57634e487b7160e01b81526011600452602481fd5b5060010190565b634e487b7160e01b600052604160045260246000fd5b6001600160a01b0381168114610d9157600080fd5b5056fea2646970667358221220085cedebabb26ed94e19a27fe2e50b5a088e341d7818c9b2795c3a377d3ab56b64736f6c63430008040033";

    public static final String FUNC___DEF_PAYOUTSTATEFORINVESTOR_STRUCT = "__def_PayoutStateForInvestor_struct";

    public static final String FUNC_ADDISSUERPAYOUTS = "addIssuerPayouts";

    public static final String FUNC_GETPAYOUTSTATESFORINVESTOR = "getPayoutStatesForInvestor";

    public static final String FUNC_GETPAYOUTSFORISSUER = "getPayoutsForIssuer";

    @Deprecated
    protected SimplePayoutService(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected SimplePayoutService(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected SimplePayoutService(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected SimplePayoutService(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public RemoteFunctionCall<PayoutStateForInvestor> __def_PayoutStateForInvestor_struct() {
        final Function function = new Function(FUNC___DEF_PAYOUTSTATEFORINVESTOR_STRUCT, 
                Arrays.<Type>asList(), 
                Arrays.<TypeReference<?>>asList(new TypeReference<PayoutStateForInvestor>() {}));
        return executeRemoteCallSingleValueReturn(function, PayoutStateForInvestor.class);
    }

    public RemoteFunctionCall<TransactionReceipt> addIssuerPayouts(String issuer, List<BigInteger> payouts) {
        final Function function = new Function(
                FUNC_ADDISSUERPAYOUTS, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, issuer), 
                new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.generated.Uint256>(
                        org.web3j.abi.datatypes.generated.Uint256.class,
                        org.web3j.abi.Utils.typeMap(payouts, org.web3j.abi.datatypes.generated.Uint256.class))), 
                Collections.<TypeReference<?>>emptyList());
        return executeRemoteCallTransaction(function);
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
                    public List call() throws Exception {
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
                    public List call() throws Exception {
                        List<Type> result = (List<Type>) executeCallSingleValueReturn(function, List.class);
                        return convertToNative(result);
                    }
                });
    }

    @Deprecated
    public static SimplePayoutService load(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new SimplePayoutService(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static SimplePayoutService load(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new SimplePayoutService(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static SimplePayoutService load(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return new SimplePayoutService(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static SimplePayoutService load(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new SimplePayoutService(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static RemoteCall<SimplePayoutService> deploy(Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(SimplePayoutService.class, web3j, credentials, contractGasProvider, BINARY, "");
    }

    @Deprecated
    public static RemoteCall<SimplePayoutService> deploy(Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(SimplePayoutService.class, web3j, credentials, gasPrice, gasLimit, BINARY, "");
    }

    public static RemoteCall<SimplePayoutService> deploy(Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return deployRemoteCall(SimplePayoutService.class, web3j, transactionManager, contractGasProvider, BINARY, "");
    }

    @Deprecated
    public static RemoteCall<SimplePayoutService> deploy(Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return deployRemoteCall(SimplePayoutService.class, web3j, transactionManager, gasPrice, gasLimit, BINARY, "");
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
