CREATE TABLE settings (
  id VARCHAR(64) NOT NULL PRIMARY KEY,
  work_minutes INT NOT NULL,
  short_break_minutes INT NOT NULL,
  long_break_minutes INT NOT NULL,
  long_break_interval INT NOT NULL,
  notifications_enabled BOOLEAN NOT NULL,
  alarm_sound VARCHAR(128) NOT NULL,
  alarm_repeat BOOLEAN NOT NULL,
  alarm_volume INT NOT NULL,
  theme VARCHAR(32) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE tasks (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  estimated_pomodoros INT NOT NULL,
  completed_pomodoros INT NOT NULL,
  done BOOLEAN NOT NULL,
  active BOOLEAN NOT NULL,
  sort_order INT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  INDEX idx_tasks_active (active),
  INDEX idx_tasks_sort (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE timer_sessions (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  mode VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  task_id BIGINT NULL,
  started_at DATETIME(6) NOT NULL,
  ended_at DATETIME(6) NULL,
  planned_seconds INT NOT NULL,
  elapsed_seconds INT NOT NULL,
  completion_reason VARCHAR(64) NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_timer_sessions_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE SET NULL,
  INDEX idx_timer_sessions_started_at (started_at),
  INDEX idx_timer_sessions_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE schedule_items (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  title VARCHAR(255) NOT NULL,
  start_at DATETIME(6) NOT NULL,
  end_at DATETIME(6) NOT NULL,
  status VARCHAR(32) NOT NULL,
  source VARCHAR(32) NOT NULL,
  task_id BIGINT NULL,
  notes TEXT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_schedule_items_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE SET NULL,
  INDEX idx_schedule_items_start_at (start_at),
  INDEX idx_schedule_items_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE interruptions (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  task_id BIGINT NULL,
  timer_session_id BIGINT NULL,
  note VARCHAR(500) NOT NULL,
  occurred_at DATETIME(6) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  CONSTRAINT fk_interruptions_task FOREIGN KEY (task_id) REFERENCES tasks (id) ON DELETE SET NULL,
  CONSTRAINT fk_interruptions_timer_session FOREIGN KEY (timer_session_id) REFERENCES timer_sessions (id) ON DELETE SET NULL,
  INDEX idx_interruptions_occurred_at (occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE agent_conversations (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  kind VARCHAR(32) NOT NULL,
  user_message TEXT NOT NULL,
  response_summary TEXT NOT NULL,
  raw_response MEDIUMTEXT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  INDEX idx_agent_conversations_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE agent_plan_drafts (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  status VARCHAR(32) NOT NULL,
  title VARCHAR(255) NOT NULL,
  advice TEXT NOT NULL,
  reasoning_summary TEXT NOT NULL,
  schedule_blocks_json MEDIUMTEXT NOT NULL,
  raw_response MEDIUMTEXT NOT NULL,
  created_at DATETIME(6) NOT NULL,
  applied_at DATETIME(6) NULL,
  INDEX idx_agent_plan_drafts_status (status),
  INDEX idx_agent_plan_drafts_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
