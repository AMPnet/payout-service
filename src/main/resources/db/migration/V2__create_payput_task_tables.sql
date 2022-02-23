CREATE TYPE payout_service.TASK_STATUS AS ENUM ('PENDING', 'SUCCESS', 'FAILED');

CREATE TABLE payout_service.create_payout_task (
    id                      UUID                       PRIMARY KEY,
    chain_id                BIGINT                     NOT NULL,
    asset_address           VARCHAR                    NOT NULL,
    block_number            NUMERIC(78)                NOT NULL,
    ignored_asset_addresses VARCHAR[]                  NOT NULL,
    requester_address       VARCHAR                    NOT NULL,
    issuer_address          VARCHAR                        NULL,
    status                  payout_service.TASK_STATUS NOT NULL,
    result_tree             UUID                           NULL REFERENCES payout_service.merkle_tree_root(id),
    tree_ipfs_hash          VARCHAR                        NULL,
    total_asset_amount      NUMERIC(78)                    NULL
);

CREATE INDEX create_payout_task_chain_id_requester_idx
    ON payout_service.create_payout_task(chain_id, requester_address);
