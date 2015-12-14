CREATE TABLE citation_event (
  id INTEGER AUTO_INCREMENT PRIMARY KEY,
  -- event_key is the unique identifier for this event.
  -- The same event may be inserted by more than one instance, but duplicates will be rejected.
  event_key VARCHAR(2048),

  -- The input event ID. May correspond to the input_event table if the input is stored.
  -- These will be namespaced by instance, but don't comprise the identity of the citation event.
  -- So this value will depend on which instance wins.
  input_event_id VARCHAR(2018),
  doi VARCHAR(1024),
  date DATETIME NOT NULL,
  url VARCHAR(4096),
  action VARCHAR(128),
  pushed BOOLEAN NOT NULL DEFAULT FALSE,
  flagged BOOLEAN NOT NULL DEFAULT FALSE,
) ENGINE innodb CHARACTER SET utf8mb4;

CREATE UNIQUE INDEX event_key ON citation_event(event_key(190));
CREATE INDEX flagged on citation_event(flagged);

CREATE TABLE input_event (
  id INTEGER AUTO_INCREMENT PRIMARY KEY,
  event_id VARCHAR(512),
  content TEXT
);

CREATE UNIQUE INDEX input_event_key ON input_event(event_id(190));

