CREATE TYPE payout_service.SNAPSHOT_FAILURE_CAUSE AS ENUM ('LOG_RESPONSE_LIMIT', 'OTHER');

ALTER TABLE payout_service.snapshot ADD COLUMN failure_cause payout_service.SNAPSHOT_FAILURE_CAUSE DEFAULT NULL;

UPDATE payout_service.snapshot SET failure_cause = 'OTHER' WHERE status = 'FAILED';
