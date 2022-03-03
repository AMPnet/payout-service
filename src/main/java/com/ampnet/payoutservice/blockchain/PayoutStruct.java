package com.ampnet.payoutservice.blockchain;

import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.DynamicStruct;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PayoutStruct extends DynamicStruct {
    public BigInteger payoutId;

    public String payoutOwner;

    public String payoutInfo;

    public Boolean isCanceled;

    public String asset;

    public BigInteger totalAssetAmount;

    public List<String> ignoredAssetAddresses;

    public byte[] assetSnapshotMerkleRoot;

    public BigInteger assetSnapshotMerkleDepth;

    public BigInteger assetSnapshotBlockNumber;

    public String assetSnapshotMerkleIpfsHash;

    public String rewardAsset;

    public BigInteger totalRewardAmount;

    public BigInteger remainingRewardAmount;

    public PayoutStruct(BigInteger payoutId, String payoutOwner, String payoutInfo, Boolean isCanceled, String asset, BigInteger totalAssetAmount, List<String> ignoredAssetAddresses, byte[] assetSnapshotMerkleRoot, BigInteger assetSnapshotMerkleDepth, BigInteger assetSnapshotBlockNumber, String assetSnapshotMerkleIpfsHash, String rewardAsset, BigInteger totalRewardAmount, BigInteger remainingRewardAmount) {
        super(new org.web3j.abi.datatypes.generated.Uint256(payoutId),new org.web3j.abi.datatypes.Address(payoutOwner),new org.web3j.abi.datatypes.Utf8String(payoutInfo),new org.web3j.abi.datatypes.Bool(isCanceled),new org.web3j.abi.datatypes.Address(asset),new org.web3j.abi.datatypes.generated.Uint256(totalAssetAmount),new org.web3j.abi.datatypes.DynamicArray<org.web3j.abi.datatypes.Address>(org.web3j.abi.datatypes.Address.class,ignoredAssetAddresses.stream().map(org.web3j.abi.datatypes.Address::new).collect(Collectors.toList())),new org.web3j.abi.datatypes.generated.Bytes32(assetSnapshotMerkleRoot),new org.web3j.abi.datatypes.generated.Uint256(assetSnapshotMerkleDepth),new org.web3j.abi.datatypes.generated.Uint256(assetSnapshotBlockNumber),new org.web3j.abi.datatypes.Utf8String(assetSnapshotMerkleIpfsHash),new org.web3j.abi.datatypes.Address(rewardAsset),new org.web3j.abi.datatypes.generated.Uint256(totalRewardAmount),new org.web3j.abi.datatypes.generated.Uint256(remainingRewardAmount));
        this.payoutId = payoutId;
        this.payoutOwner = payoutOwner;
        this.payoutInfo = payoutInfo;
        this.isCanceled = isCanceled;
        this.asset = asset;
        this.totalAssetAmount = totalAssetAmount;
        this.ignoredAssetAddresses = ignoredAssetAddresses;
        this.assetSnapshotMerkleRoot = assetSnapshotMerkleRoot;
        this.assetSnapshotMerkleDepth = assetSnapshotMerkleDepth;
        this.assetSnapshotBlockNumber = assetSnapshotBlockNumber;
        this.assetSnapshotMerkleIpfsHash = assetSnapshotMerkleIpfsHash;
        this.rewardAsset = rewardAsset;
        this.totalRewardAmount = totalRewardAmount;
        this.remainingRewardAmount = remainingRewardAmount;
    }

    public PayoutStruct(Uint256 payoutId, Address payoutOwner, Utf8String payoutInfo, Bool isCanceled, Address asset, Uint256 totalAssetAmount, DynamicArray<Address> ignoredAssetAddresses, Bytes32 assetSnapshotMerkleRoot, Uint256 assetSnapshotMerkleDepth, Uint256 assetSnapshotBlockNumber, Utf8String assetSnapshotMerkleIpfsHash, Address rewardAsset, Uint256 totalRewardAmount, Uint256 remainingRewardAmount) {
        super(payoutId,payoutOwner,payoutInfo,isCanceled,asset,totalAssetAmount,ignoredAssetAddresses,assetSnapshotMerkleRoot,assetSnapshotMerkleDepth,assetSnapshotBlockNumber,assetSnapshotMerkleIpfsHash,rewardAsset,totalRewardAmount,remainingRewardAmount);
        this.payoutId = payoutId.getValue();
        this.payoutOwner = payoutOwner.getValue();
        this.payoutInfo = payoutInfo.getValue();
        this.isCanceled = isCanceled.getValue();
        this.asset = asset.getValue();
        this.totalAssetAmount = totalAssetAmount.getValue();
        this.ignoredAssetAddresses = ignoredAssetAddresses.getValue().stream().map(org.web3j.abi.datatypes.Address::getValue).collect(Collectors.toList());
        this.assetSnapshotMerkleRoot = assetSnapshotMerkleRoot.getValue();
        this.assetSnapshotMerkleDepth = assetSnapshotMerkleDepth.getValue();
        this.assetSnapshotBlockNumber = assetSnapshotBlockNumber.getValue();
        this.assetSnapshotMerkleIpfsHash = assetSnapshotMerkleIpfsHash.getValue();
        this.rewardAsset = rewardAsset.getValue();
        this.totalRewardAmount = totalRewardAmount.getValue();
        this.remainingRewardAmount = remainingRewardAmount.getValue();
    }

    @Override
    public String toString() {
        return "Payout{" +
                "payoutId=" + payoutId +
                ", payoutOwner='" + payoutOwner + '\'' +
                ", payoutInfo='" + payoutInfo + '\'' +
                ", isCanceled=" + isCanceled +
                ", asset='" + asset + '\'' +
                ", totalAssetAmount=" + totalAssetAmount +
                ", ignoredAssetAddresses=" + ignoredAssetAddresses +
                ", assetSnapshotMerkleRoot=" + Arrays.toString(assetSnapshotMerkleRoot) +
                ", assetSnapshotMerkleDepth=" + assetSnapshotMerkleDepth +
                ", assetSnapshotBlockNumber=" + assetSnapshotBlockNumber +
                ", assetSnapshotMerkleIpfsHash='" + assetSnapshotMerkleIpfsHash + '\'' +
                ", rewardAsset='" + rewardAsset + '\'' +
                ", totalRewardAmount=" + totalRewardAmount +
                ", remainingRewardAmount=" + remainingRewardAmount +
                '}';
    }
}
