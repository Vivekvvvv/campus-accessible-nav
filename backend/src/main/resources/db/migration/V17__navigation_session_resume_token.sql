-- V17__navigation_session_resume_token.sql
-- 会话恢复：增加恢复令牌

ALTER TABLE t_navigation_session
  ADD COLUMN IF NOT EXISTS resume_token VARCHAR(64) NULL;

CREATE INDEX IF NOT EXISTS idx_navigation_session_resume_token
  ON t_navigation_session (resume_token);
