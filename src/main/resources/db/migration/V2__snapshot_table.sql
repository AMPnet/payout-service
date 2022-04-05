CREATE TYPE payout_service.SNAPSHOT_STATUS AS ENUM ('PENDING', 'SUCCESS', 'FAILED');

CREATE TABLE payout_service.snapshot (
    id                       UUID                           PRIMARY KEY,
    name                     VARCHAR                        NOT NULL,
    chain_id                 BIGINT                         NOT NULL,
    asset_address            VARCHAR                        NOT NULL,
    block_number             NUMERIC(78)                    NOT NULL,
    ignored_holder_addresses VARCHAR[]                      NOT NULL,
    owner_address            VARCHAR                        NOT NULL,
    status                   payout_service.SNAPSHOT_STATUS NOT NULL,
    result_tree              UUID                               NULL REFERENCES payout_service.merkle_tree_root(id),
    tree_ipfs_hash           VARCHAR                            NULL,
    total_asset_amount       NUMERIC(78)                        NULL
);

CREATE INDEX snapshot_chain_id_owner_idx ON payout_service.snapshot(chain_id, owner_address);
CREATE INDEX snapshot_chain_id_idx ON payout_service.snapshot(chain_id);
CREATE INDEX snapshot_status_idx ON payout_service.snapshot(status);
CREATE INDEX snapshot_owner_address_idx ON payout_service.snapshot(owner_address);
