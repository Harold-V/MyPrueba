CREATE TABLE users (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    email         VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMP    NOT NULL
);

CREATE TABLE businesses (
    id            VARCHAR(36)  NOT NULL PRIMARY KEY,
    owner_user_id VARCHAR(36)  NOT NULL UNIQUE REFERENCES users (id),
    name          VARCHAR(80)  NOT NULL,
    category      VARCHAR(20),
    currency      VARCHAR(3)   NOT NULL,
    time_zone     VARCHAR(40)  NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL
);

CREATE TABLE sessions (
    token_hash   VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id      VARCHAR(36) NOT NULL REFERENCES users (id),
    expires_at   TIMESTAMP   NOT NULL,
    last_seen_at TIMESTAMP   NOT NULL,
    created_at   TIMESTAMP   NOT NULL
);

CREATE INDEX idx_sessions_user ON sessions (user_id);
