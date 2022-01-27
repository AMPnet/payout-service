// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

contract MerkleTreePathValidator {

    bytes32 private _merkleTreeRoot;

    struct PathSegment {
        bytes32 siblingHash;
        bool isLeft;
    }

    constructor(bytes32 merkleTreeRoot) {
        _merkleTreeRoot = merkleTreeRoot;
    }

    // needed for web3j to correctly generate PathSegment structure
    function __web3j_array_fix__(PathSegment memory _path_segment_fix_) public pure {}

    function containsNode(address wallet, uint256 balance, PathSegment[] calldata path) external view returns (bool) {
        bytes32 currentHash = keccak256(abi.encode(wallet, balance));

        for (uint i = 0; i < path.length; i++) {
            if (path[i].isLeft) {
                currentHash = keccak256(abi.encode(path[i].siblingHash, currentHash));
            } else {
                currentHash = keccak256(abi.encode(currentHash, path[i].siblingHash));
            }
        }

        return currentHash == _merkleTreeRoot;
    }
}
