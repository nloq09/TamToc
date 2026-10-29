CREATE TABLE world_event_cycles (
 event_key VARCHAR(100) NOT NULL,
 cycle BIGINT NOT NULL,
 started_at TIMESTAMPTZ NOT NULL,
 PRIMARY KEY (event_key, cycle)
);
