CREATE TABLE article_reviews (
 id BIGINT PRIMARY KEY AUTO_INCREMENT,
 work_id BIGINT NOT NULL,
 draft_revision BIGINT NOT NULL,
 payload MEDIUMTEXT NOT NULL,
 findings VARCHAR(1000) NOT NULL,
 status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
 decision_note VARCHAR(500) NOT NULL DEFAULT '',
 reviewer_id BIGINT NULL,
 created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 decided_at TIMESTAMP NULL,
 INDEX idx_article_reviews_status (status,id),
 INDEX idx_article_reviews_work (work_id,id),
 FOREIGN KEY (work_id) REFERENCES works(id) ON DELETE CASCADE,
 FOREIGN KEY (reviewer_id) REFERENCES members(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
