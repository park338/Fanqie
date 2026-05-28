CREATE TABLE timer_states (
  id VARCHAR(64) NOT NULL PRIMARY KEY,
  mode VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  total_seconds INT NOT NULL,
  elapsed_seconds INT NOT NULL,
  session_id BIGINT NULL,
  last_started_at DATETIME(6) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_timer_states_session FOREIGN KEY (session_id) REFERENCES timer_sessions (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
