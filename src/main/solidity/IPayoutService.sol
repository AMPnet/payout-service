// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

import "./Structs.sol";

interface IPayoutService {

    struct PayoutStateForInvestor {
        uint256 payoutId;
        address investor;
        uint256 amountClaimed;
    }

    function __def_Payout_struct__() external view returns (Structs.Payout memory);
    function __def_PayoutStateForInvestor_struct__() external view returns (PayoutStateForInvestor memory);

    function getPayoutsForIssuer(
        address issuer,
        address payoutManager,
        address[] memory assetFactories
    ) external view returns (Structs.Payout[] memory);

    function getPayoutStatesForInvestor(
        address investor,
        address payoutManager,
        uint256[] memory payoutIds
    ) external view returns (PayoutStateForInvestor[] memory);

    function getPayoutFeeForAssetAndAmount(
        address asset,
        uint256 amount,
        address payoutManager
    ) external view returns (address treasury, uint256 fee);
}
