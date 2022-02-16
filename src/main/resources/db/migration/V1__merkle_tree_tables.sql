CREATE TYPE payout_service.HASH_FUNCTION AS ENUM ('IDENTITY', 'FIXED', 'KECCAK_256');

CREATE TABLE payout_service.merkle_tree_root (
    id               UUID                         PRIMARY KEY,
    chain_id         BIGINT                       NOT NULL,
    asset_address    VARCHAR                      NOT NULL,
    block_number     NUMERIC(78)                  NOT NULL,
    hash             VARCHAR                      NOT NULL,
    hash_fn          payout_service.HASH_FUNCTION NOT NULL
);

CREATE UNIQUE INDEX merkle_tree_root_idx ON payout_service.merkle_tree_root(chain_id, asset_address, hash);

CREATE TABLE payout_service.merkle_tree_leaf_node (
    id          UUID        PRIMARY KEY,
    merkle_root UUID        NOT NULL REFERENCES payout_service.merkle_tree_root(id),
    address     VARCHAR     NOT NULL,
    balance     NUMERIC(78) NOT NULL
);

CREATE INDEX merkle_tree_leaf_node_root_idx ON payout_service.merkle_tree_leaf_node(merkle_root);
CREATE INDEX merkle_tree_leaf_node_address_idx ON payout_service.merkle_tree_leaf_node(address);
CREATE UNIQUE INDEX merkle_tree_leaf_node_exists_idx ON payout_service.merkle_tree_leaf_node(merkle_root, address);
