CREATE TABLE citation_event (
  id INTEGER AUTO_INCREMENT PRIMARY KEY,
  event_key VARCHAR(2048),
  doi VARCHAR(1024),
  date DATETIME NOT NULL,
  url VARCHAR(4096),
  action VARCHAR(128),
  pushed BOOLEAN NOT NULL DEFAULT FALSE,
  flagged BOOLEAN NOT NULL DEFAULT FALSE,
) ENGINE innodb CHARACTER SET utf8mb4;

CREATE UNIQUE INDEX event_key ON citation_event(event_key(190));
CREATE INDEX flagged on citation_event(flagged);