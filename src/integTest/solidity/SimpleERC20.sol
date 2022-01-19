// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

contract SimpleERC20 {

    event Transfer(address indexed from, address indexed to, uint256 value);

    mapping(address => uint256) private _balances;

    constructor(address[] memory accounts, uint256[] memory balances) {
        require(accounts.length == balances.length, "Mismatching account and balance array lengths");

        for (uint i = 0; i < accounts.length; i++) {
            _balances[accounts[i]] = balances[i];
        }
    }

    function balanceOf(address account) public view returns (uint256) {
        return _balances[account];
    }

    function transfer(address recipient, uint256 amount) public returns (bool) {
        require(recipient != address(0), "Transfer to the zero address");
        require(_balances[msg.sender] >= amount, "Transfer amount exceeds balance");

        _balances[msg.sender] -= amount;
        _balances[recipient] += amount;

        emit Transfer(msg.sender, recipient, amount);

        return true;
    }
}
