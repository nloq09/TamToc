CREATE TABLE dungeon_runs (
 id UUID PRIMARY KEY,
 instance_id UUID NOT NULL UNIQUE,
 dungeon_id VARCHAR(40) NOT NULL,
 party_id UUID NOT NULL,
 server_node VARCHAR(80) NOT NULL,
 status VARCHAR(24) NOT NULL,
 started_at TIMESTAMPTZ NOT NULL,
 ended_at TIMESTAMPTZ,
 result TEXT
);
CREATE TABLE dungeon_run_participants (
 run_id UUID NOT NULL REFERENCES dungeon_runs(id),
 character_id UUID NOT NULL REFERENCES characters(id),
 eligible BOOLEAN NOT NULL DEFAULT false,
 result TEXT,
 PRIMARY KEY (run_id, character_id)
);
CREATE INDEX dungeon_participant_character_idx ON dungeon_run_participants(character_id);
CREATE TABLE dungeon_reward_grants (
 run_id UUID NOT NULL,
 character_id UUID NOT NULL,
 reward_key VARCHAR(40) NOT NULL,
 granted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 PRIMARY KEY (run_id,character_id,reward_key),
 FOREIGN KEY (run_id,character_id) REFERENCES dungeon_run_participants(run_id,character_id)
);
CREATE TABLE duel_results (
 id UUID PRIMARY KEY,
 challenger_id UUID NOT NULL REFERENCES characters(id),
 opponent_id UUID NOT NULL REFERENCES characters(id),
 winner_id UUID REFERENCES characters(id),
 reason VARCHAR(40) NOT NULL,
 started_at TIMESTAMPTZ NOT NULL,
 ended_at TIMESTAMPTZ NOT NULL,
 CHECK (challenger_id <> opponent_id),
 CHECK (winner_id IS NULL OR winner_id=challenger_id OR winner_id=opponent_id)
);
CREATE INDEX duel_challenger_idx ON duel_results(challenger_id,ended_at);
CREATE INDEX duel_opponent_idx ON duel_results(opponent_id,ended_at);
