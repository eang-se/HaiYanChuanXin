INSERT INTO tags(name,description,is_activity,kind)
VALUES('海燕精选','由海燕文学社编辑部选出的好作品。',FALSE,'TOPIC')
ON DUPLICATE KEY UPDATE name=name;

CREATE TABLE site_visitors (
 visitor_hash CHAR(64) PRIMARY KEY,
 first_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 last_seen TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 INDEX idx_site_visitors_online(last_seen)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
