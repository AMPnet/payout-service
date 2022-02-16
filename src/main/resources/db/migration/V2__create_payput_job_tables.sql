CREATE TYPE payout_service.JOB_STATUS AS ENUM ('PENDING', 'SUCCESS', 'FAILED');

CREATE TABLE payout_service.create_payout_job (
    id                      UUID                      PRIMARY KEY,
    chain_id                BIGINT                    NOT NULL,
    asset_address           VARCHAR                   NOT NULL,
    block_number            NUMERIC(78)               NOT NULL,
    ignored_asset_addresses VARCHAR[]                 NOT NULL,
    requester_address       VARCHAR                   NOT NULL,
    status                  payout_service.JOB_STATUS NOT NULL
);

CREATE UNIQUE INDEX create_payout_job_chain_id_requester_idx
    ON payout_service.create_payout_job(chain_id, requester_address);
