CREATE TABLE character_progress (
 character_id UUID PRIMARY KEY REFERENCES characters(id) ON DELETE CASCADE,
 data TEXT NOT NULL
);
