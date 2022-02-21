// SPDX-License-Identifier: MIT
pragma solidity ^0.8.0;

import "./SimplePayoutManager.sol";
import "./Structs.sol";

contract SimplePayoutService {

    struct PayoutStateForInvestor {
        uint256 payoutId;
        address investor;
        uint256 amountClaimed;
    }

    mapping(address => uint256[]) private payoutsByIssuer;

    function __def_PayoutStateForInvestor_struct() external pure returns (PayoutStateForInvestor memory) {
        return PayoutStateForInvestor(0, address(0), 0);
    }

    function addIssuerPayouts(address issuer, uint256[] memory payouts) external {
        payoutsByIssuer[issuer] = payouts;
    }

    function getPayoutsForIssuer(
        address issuer,
        address payoutManager,
        address[] memory assetFactories
    ) external view returns (Structs.Payout[] memory) {
        uint256[] memory payoutIds = payoutsByIssuer[issuer];
        uint256 responseItemsCount = payoutIds.length;

        if (responseItemsCount == 0) {
            return new Structs.Payout[](0);
        }

        Structs.Payout[] memory payoutsResponse = new Structs.Payout[](responseItemsCount);
        SimplePayoutManager payoutManagerInstance = SimplePayoutManager(payoutManager);

        for (uint256 i = 0; i < responseItemsCount; i++) {
            payoutsResponse[i] = payoutManagerInstance.getPayoutInfo(payoutIds[i]);
        }

        return payoutsResponse;
    }

    function getPayoutStatesForInvestor(
        address investor,
        address payoutManager,
        uint256[] memory payoutIds
    ) external view returns (PayoutStateForInvestor[] memory) {
        uint256 payoutIdsCount = payoutIds.length;

        if (payoutIdsCount == 0) {
            return new PayoutStateForInvestor[](0);
        }

        PayoutStateForInvestor[] memory response = new PayoutStateForInvestor[](payoutIdsCount);
        SimplePayoutManager payoutManagerInstance = SimplePayoutManager(payoutManager);

        for (uint256 i = 0; i < payoutIdsCount; i++) {
            uint256 payoutId = payoutIds[i];
            response[i] = PayoutStateForInvestor(
                payoutId,
                investor,
                payoutManagerInstance.getAmountOfClaimedFunds(payoutId, investor)
            );
        }

        return response;
    }
}
