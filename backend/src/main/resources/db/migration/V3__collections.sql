CREATE TABLE collections (
 id BIGINT PRIMARY KEY AUTO_INCREMENT, author_id BIGINT NOT NULL,
 title VARCHAR(100) NOT NULL, description VARCHAR(1000) NOT NULL DEFAULT '',
 kind VARCHAR(12) NOT NULL DEFAULT '选集', published BOOLEAN NOT NULL DEFAULT FALSE,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 FOREIGN KEY(author_id) REFERENCES members(id), INDEX idx_collections_author(author_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE collection_works (
 collection_id BIGINT NOT NULL, work_id BIGINT NOT NULL, position INT NOT NULL,
 PRIMARY KEY(collection_id,work_id), UNIQUE KEY(collection_id,position),
 FOREIGN KEY(collection_id) REFERENCES collections(id), FOREIGN KEY(work_id) REFERENCES works(id)
);
