CREATE TABLE remembered_devices (
 selector CHAR(32) PRIMARY KEY,
 token_hash CHAR(64) NOT NULL,
 credential_hash CHAR(64) NOT NULL,
 member_id BIGINT NOT NULL,
 expires_at TIMESTAMP NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(member_id) REFERENCES members(id) ON DELETE CASCADE,
 INDEX idx_remembered_member(member_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
