CREATE SEQUENCE IF NOT EXISTS app_users_seq START WITH 100;
CREATE SEQUENCE IF NOT EXISTS vehicles_seq START WITH 100;
CREATE SEQUENCE IF NOT EXISTS parking_zones_seq START WITH 100;
CREATE SEQUENCE IF NOT EXISTS parking_sessions_seq START WITH 100;
CREATE SEQUENCE IF NOT EXISTS parking_passes_seq START WITH 100;

CREATE TABLE IF NOT EXISTS app_users (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL CHECK (LENGTH(TRIM(name)) > 0),
    email VARCHAR(254) NOT NULL UNIQUE CHECK (email = LOWER(email))
);
CREATE TABLE IF NOT EXISTS vehicles (
    id BIGINT PRIMARY KEY,
    plate_number VARCHAR(15) NOT NULL UNIQUE CHECK (LENGTH(plate_number) >= 3),
    owner_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS parking_zones (
    id BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL CHECK (LENGTH(TRIM(name)) > 0),
    capacity INT NOT NULL CHECK (capacity BETWEEN 1 AND 100000),
    hourly_price NUMERIC(12,2) NOT NULL CHECK (hourly_price BETWEEN 0 AND 1000000)
);
CREATE TABLE IF NOT EXISTS parking_sessions (
    id BIGINT PRIMARY KEY,
    vehicle_id BIGINT NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    zone_id BIGINT NOT NULL REFERENCES parking_zones(id) ON DELETE CASCADE,
    entry_time TIMESTAMPTZ(6) NOT NULL,
    exit_time TIMESTAMPTZ(6),
    amount NUMERIC(24,2) NOT NULL CHECK (amount >= 0),
    hourly_price NUMERIC(12,2) NOT NULL CHECK (hourly_price BETWEEN 0 AND 1000000),
    CHECK (exit_time IS NULL OR exit_time >= entry_time),
    CHECK (exit_time IS NOT NULL OR amount = 0)
);
CREATE TABLE IF NOT EXISTS parking_passes (
    id BIGINT PRIMARY KEY,
    vehicle_id BIGINT NOT NULL REFERENCES vehicles(id) ON DELETE CASCADE,
    zone_id BIGINT NOT NULL REFERENCES parking_zones(id) ON DELETE CASCADE,
    valid_from TIMESTAMPTZ(6) NOT NULL,
    valid_until TIMESTAMPTZ(6) NOT NULL,
    status VARCHAR(9) NOT NULL CHECK (status IN ('ACTIVE', 'CANCELLED')),
    CHECK (valid_from < valid_until)
);
CREATE TABLE IF NOT EXISTS operation_lock (id BIGINT PRIMARY KEY);
CREATE TABLE IF NOT EXISTS seed_history (id INT PRIMARY KEY);
INSERT INTO operation_lock(id) VALUES (1) ON CONFLICT (id) DO NOTHING;
CREATE UNIQUE INDEX IF NOT EXISTS sessions_one_open_per_vehicle
    ON parking_sessions(vehicle_id) WHERE exit_time IS NULL;
CREATE INDEX IF NOT EXISTS sessions_zone ON parking_sessions(zone_id, exit_time);
CREATE INDEX IF NOT EXISTS passes_vehicle ON parking_passes(vehicle_id, zone_id);
CREATE INDEX IF NOT EXISTS vehicles_owner ON vehicles(owner_id);
