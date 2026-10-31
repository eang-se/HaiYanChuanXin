ALTER TABLE members ADD COLUMN account_type VARCHAR(12) NOT NULL DEFAULT 'PERSONAL';
CREATE TABLE public_account_members (
 public_member_id BIGINT NOT NULL, member_id BIGINT NOT NULL,
 PRIMARY KEY(public_member_id,member_id),
 FOREIGN KEY(public_member_id) REFERENCES members(id) ON DELETE CASCADE,
 FOREIGN KEY(member_id) REFERENCES members(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE public_account_sessions (
 public_member_id BIGINT NOT NULL, actor_id BIGINT NOT NULL, session_hash CHAR(64) NOT NULL,
 last_seen TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(public_member_id,actor_id,session_hash), UNIQUE KEY uq_public_session(session_hash),
 KEY idx_public_seen(public_member_id,last_seen),
 FOREIGN KEY(public_member_id) REFERENCES members(id) ON DELETE CASCADE,
 FOREIGN KEY(actor_id) REFERENCES members(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE public_identity_audit (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, actor_member_id BIGINT NULL, public_member_id BIGINT NULL,
 action_method VARCHAR(8) NOT NULL, action_path VARCHAR(255) NOT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(actor_member_id) REFERENCES members(id) ON DELETE SET NULL,
 FOREIGN KEY(public_member_id) REFERENCES members(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
