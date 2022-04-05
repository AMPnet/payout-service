// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

interface IAssetCommon {

    struct AssetCommonState {
        string flavor;
        string version;
        address contractAddress;
        address owner;
        string info;
        string name;
        string symbol;
        uint256 totalSupply;
        uint8 decimals;
        address issuer;
    }

    function commonState() external view returns (AssetCommonState memory);
}
