package com.ampnet.payoutservice.blockchain;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.StaticStruct;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Bytes32;
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
public class MerkleTreePathValidator extends Contract {
    public static final String BINARY = "608060405234801561001057600080fd5b506040516103ae3803806103ae83398101604081905261002f91610037565b60005561004f565b600060208284031215610048578081fd5b5051919050565b6103508061005e6000396000f3fe608060405234801561001057600080fd5b50600436106100365760003560e01c80632d7bd1831461003b578063b2b6fe8c1461004e575b600080fd5b61004c610049366004610294565b50565b005b61006161005c3660046101e1565b610075565b604051901515815260200160405180910390f35b604080516001600160a01b0386166020820152908101849052600090819060600160405160208183030381529060405280519060200120905060005b838110156101be578484828181106100d957634e487b7160e01b600052603260045260246000fd5b90506040020160200160208101906100f19190610273565b1561014e5784848281811061011657634e487b7160e01b600052603260045260246000fd5b6040805191810293909301356020820152918201849052506060016040516020818303038152906040528051906020012091506101ac565b8185858381811061016f57634e487b7160e01b600052603260045260246000fd5b90506040020160000135604051602001610193929190918252602082015260400190565b6040516020818303038152906040528051906020012091505b806101b6816102f3565b9150506100b1565b506000541495945050505050565b803580151581146101dc57600080fd5b919050565b600080600080606085870312156101f6578384fd5b84356001600160a01b038116811461020c578485fd5b935060208501359250604085013567ffffffffffffffff8082111561022f578384fd5b818701915087601f830112610242578384fd5b813581811115610250578485fd5b8860208260061b8501011115610264578485fd5b95989497505060200194505050565b600060208284031215610284578081fd5b61028d826101cc565b9392505050565b6000604082840312156102a5578081fd5b6040516040810181811067ffffffffffffffff821117156102d457634e487b7160e01b83526041600452602483fd5b604052823581526102e7602084016101cc565b60208201529392505050565b600060001982141561031357634e487b7160e01b81526011600452602481fd5b506001019056fea26469706673582212207d6ae6951800e2e3c13d21e931709b6fd55b237c0dc9c55b53160f1603ead90f64736f6c63430008040033";

    public static final String FUNC___WEB3J_ARRAY_FIX__ = "__web3j_array_fix__";

    public static final String FUNC_CONTAINSNODE = "containsNode";

    @Deprecated
    protected MerkleTreePathValidator(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    protected MerkleTreePathValidator(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, credentials, contractGasProvider);
    }

    @Deprecated
    protected MerkleTreePathValidator(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        super(BINARY, contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    protected MerkleTreePathValidator(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        super(BINARY, contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public RemoteFunctionCall<Boolean> containsNode(String wallet, BigInteger balance, List<PathSegment> path) {
        final Function function = new Function(FUNC_CONTAINSNODE, 
                Arrays.<Type>asList(new org.web3j.abi.datatypes.Address(160, wallet), 
                new org.web3j.abi.datatypes.generated.Uint256(balance), 
                new org.web3j.abi.datatypes.DynamicArray<PathSegment>(PathSegment.class, path)), 
                Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
        return executeRemoteCallSingleValueReturn(function, Boolean.class);
    }

    @Deprecated
    public static MerkleTreePathValidator load(String contractAddress, Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit) {
        return new MerkleTreePathValidator(contractAddress, web3j, credentials, gasPrice, gasLimit);
    }

    @Deprecated
    public static MerkleTreePathValidator load(String contractAddress, Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit) {
        return new MerkleTreePathValidator(contractAddress, web3j, transactionManager, gasPrice, gasLimit);
    }

    public static MerkleTreePathValidator load(String contractAddress, Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider) {
        return new MerkleTreePathValidator(contractAddress, web3j, credentials, contractGasProvider);
    }

    public static MerkleTreePathValidator load(String contractAddress, Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider) {
        return new MerkleTreePathValidator(contractAddress, web3j, transactionManager, contractGasProvider);
    }

    public static RemoteCall<MerkleTreePathValidator> deploy(Web3j web3j, Credentials credentials, ContractGasProvider contractGasProvider, byte[] merkleTreeRoot) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(merkleTreeRoot)));
        return deployRemoteCall(MerkleTreePathValidator.class, web3j, credentials, contractGasProvider, BINARY, encodedConstructor);
    }

    public static RemoteCall<MerkleTreePathValidator> deploy(Web3j web3j, TransactionManager transactionManager, ContractGasProvider contractGasProvider, byte[] merkleTreeRoot) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(merkleTreeRoot)));
        return deployRemoteCall(MerkleTreePathValidator.class, web3j, transactionManager, contractGasProvider, BINARY, encodedConstructor);
    }

    @Deprecated
    public static RemoteCall<MerkleTreePathValidator> deploy(Web3j web3j, Credentials credentials, BigInteger gasPrice, BigInteger gasLimit, byte[] merkleTreeRoot) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(merkleTreeRoot)));
        return deployRemoteCall(MerkleTreePathValidator.class, web3j, credentials, gasPrice, gasLimit, BINARY, encodedConstructor);
    }

    @Deprecated
    public static RemoteCall<MerkleTreePathValidator> deploy(Web3j web3j, TransactionManager transactionManager, BigInteger gasPrice, BigInteger gasLimit, byte[] merkleTreeRoot) {
        String encodedConstructor = FunctionEncoder.encodeConstructor(Arrays.<Type>asList(new org.web3j.abi.datatypes.generated.Bytes32(merkleTreeRoot)));
        return deployRemoteCall(MerkleTreePathValidator.class, web3j, transactionManager, gasPrice, gasLimit, BINARY, encodedConstructor);
    }

    public static class PathSegment extends StaticStruct {
        public byte[] siblingHash;

        public Boolean isLeft;

        public PathSegment(byte[] siblingHash, Boolean isLeft) {
            super(new org.web3j.abi.datatypes.generated.Bytes32(siblingHash),new org.web3j.abi.datatypes.Bool(isLeft));
            this.siblingHash = siblingHash;
            this.isLeft = isLeft;
        }

        public PathSegment(Bytes32 siblingHash, Bool isLeft) {
            super(siblingHash,isLeft);
            this.siblingHash = siblingHash.getValue();
            this.isLeft = isLeft.getValue();
        }
    }
}
