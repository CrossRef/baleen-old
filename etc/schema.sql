CREATE TABLE event (
  id INTEGER AUTO_INCREMENT PRIMARY KEY,
  new_id INTEGER NOT NULL,
  old_id INTEGER NULL,
  doi VARCHAR(1024),
  inserted DATETIME NOT NULL,
  server VARCHAR(1024),
  title VARCHAR(1024),
  url VARCHAR(1024),
  action VARCHAR(128)
) ENGINE innodb CHARACTER SET utf8mb4;

