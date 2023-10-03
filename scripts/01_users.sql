ALTER ROLE clo_user SUPERUSER;
CREATE EXTENSION "pg_trgm";
CREATE EXTENSION "uuid-ossp";
CREATE TABLE pessoas (id uuid PRIMARY KEY DEFAULT uuid_generate_v4(),
                               apelido varchar(32) UNIQUE NOT NULL,
                               nome varchar(100) NOT NULL,
                               nascimento DATE NOT NULL,
                               stack TEXT,
                               search TEXT);
CREATE INDEX idx_gin ON pessoas USING gin (search gin_trgm_ops);
CREATE INDEX idx_btree ON pessoas (search);
