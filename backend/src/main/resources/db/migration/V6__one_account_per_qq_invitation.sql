CREATE TABLE qq_invitation_bindings (
 qq_number VARCHAR(12) PRIMARY KEY,
 member_id BIGINT UNIQUE,
 bound_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT fk_qq_binding_member FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
INSERT INTO qq_invitation_bindings(qq_number,member_id)
 SELECT qq_number,MIN(id) FROM members WHERE qq_number = '12345' GROUP BY qq_number;
INSERT INTO qq_invitation_bindings(qq_number,member_id)
 SELECT qq_number,id FROM members WHERE qq_number <> '' AND qq_number <> '12345';
