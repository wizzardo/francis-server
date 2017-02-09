DROP TABLE IF EXISTS application;
CREATE TABLE application (
  id   SERIAL PRIMARY KEY,
  name VARCHAR(128)
);
ALTER TABLE application
  ADD UNIQUE (name);

DROP TABLE IF EXISTS instance;
CREATE TABLE instance (
  id             SERIAL PRIMARY KEY,
  application_id INT,
  version        VARCHAR(64),
  ip             VARCHAR(16),
  mac            CHAR(17),
  hostname       VARCHAR(128)
);
ALTER TABLE instance
  ADD UNIQUE (application_id, mac);

DROP TABLE IF EXISTS transformation;
CREATE TABLE transformation (
  id               SERIAL PRIMARY KEY,
  application_id   INT,
  version          INT,
  last_updated     TIMESTAMP,
  date_created     TIMESTAMP,
  class_name       VARCHAR(256),
  method           VARCHAR(128),
  method_descriptor VARCHAR(512),
  before           VARCHAR(1024),
  after            VARCHAR(1024),
  variables        VARCHAR(1024)
);
