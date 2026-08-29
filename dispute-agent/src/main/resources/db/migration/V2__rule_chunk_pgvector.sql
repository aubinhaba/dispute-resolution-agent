-- Flyway owns this DDL even though Spring AI could create it: two owners for one schema
-- means nobody knows what is really in the database. See ADR-0005.

CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE rule_chunk (
    -- 384 is the only coupling between this file and the embedding model (see ADR-0011):
    -- changing model would force a migration, which is exactly the signal we want
    id        UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    content   TEXT,
    metadata  JSON,
    embedding VECTOR(384)
);

-- On 90 chunks this index accelerates nothing; it is here for the corpus that will grow
CREATE INDEX rule_chunk_embedding_idx
    ON rule_chunk USING HNSW (embedding vector_cosine_ops);
