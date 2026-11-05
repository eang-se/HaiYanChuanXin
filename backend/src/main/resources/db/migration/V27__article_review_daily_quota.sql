CREATE TABLE article_review_daily_quota (
 member_id BIGINT NOT NULL,
 review_day DATE NOT NULL,
 attempts INT NOT NULL DEFAULT 0,
 PRIMARY KEY (member_id, review_day),
 FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE article_review_quota_receipts (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 member_id BIGINT NOT NULL,
 review_day DATE NOT NULL,
 snapshot_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
 manual_claimed BOOLEAN NOT NULL DEFAULT FALSE,
 INDEX idx_review_receipt (member_id,review_day,snapshot_hash,manual_claimed,id),
 FOREIGN KEY (member_id,review_day) REFERENCES article_review_daily_quota(member_id,review_day) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
