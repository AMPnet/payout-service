CREATE TYPE payout_service.HASH_FUNCTION AS ENUM ('IDENTITY', 'FIXED', 'SIMPLE', 'KECCAK_256');

CREATE TABLE payout_service.merkle_tree_root (
    id               UUID                         PRIMARY KEY,
    chain_id         BIGINT                       NOT NULL,
    contract_address VARCHAR                      NOT NULL,
    block_number     NUMERIC(78)                  NOT NULL,
    hash             VARCHAR                      NOT NULL,
    hash_fn          payout_service.HASH_FUNCTION NOT NULL
);

CREATE UNIQUE INDEX merkle_tree_leaf_node_idx
    ON payout_service.merkle_tree_root(chain_id, contract_address, block_number, hash);

CREATE TABLE payout_service.merkle_tree_leaf_node (
    id          UUID        PRIMARY KEY,
    merkle_root UUID        NOT NULL REFERENCES payout_service.merkle_tree_root(id),
    address     VARCHAR     NOT NULL,
    balance     NUMERIC(78) NOT NULL
);
