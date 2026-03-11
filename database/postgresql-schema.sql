CREATE TABLE IF NOT EXISTS persons (
    id VARCHAR(80) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    age VARCHAR(32),
    last_seen VARCHAR(255),
    contact VARCHAR(255),
    notes TEXT,
    status VARCHAR(64),
    image_path VARCHAR(255),
    descriptor_text TEXT NOT NULL,
    created_at VARCHAR(64) NOT NULL
);

CREATE TABLE IF NOT EXISTS match_history (
    id VARCHAR(80) PRIMARY KEY,
    matched BOOLEAN NOT NULL,
    person_id VARCHAR(80),
    person_name VARCHAR(255),
    status VARCHAR(64),
    distance_value VARCHAR(32),
    threshold_value VARCHAR(32),
    snapshot_path VARCHAR(255),
    created_at VARCHAR(64) NOT NULL
);

CREATE TABLE IF NOT EXISTS users (
    email VARCHAR(255) PRIMARY KEY,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(32) NOT NULL
);
