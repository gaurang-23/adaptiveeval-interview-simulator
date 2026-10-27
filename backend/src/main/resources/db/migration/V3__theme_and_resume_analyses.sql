-- V3__theme_and_resume_analyses.sql
-- Adds persistent theme preference to users and creates resume_analyses table for AI Resume Screener

ALTER TABLE users
    ADD COLUMN theme_preference VARCHAR(20) NULL DEFAULT NULL;

CREATE TABLE resume_analyses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    file_content_type VARCHAR(120) NOT NULL DEFAULT 'application/pdf',
    file_size_bytes BIGINT NOT NULL DEFAULT 0,
    job_title VARCHAR(200) NULL,
    job_description TEXT NOT NULL,
    overall_score INT NULL,
    dimension_scores_json JSON NULL,
    analysis_result_json JSON NULL,
    ai_provider VARCHAR(80) NULL,
    ai_model VARCHAR(120) NULL,
    analysis_status VARCHAR(40) NOT NULL DEFAULT 'PROCESSING',
    error_category VARCHAR(80) NULL,
    error_message VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_resume_analyses_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_resume_analyses_user_created (user_id, created_at),
    INDEX idx_resume_analyses_user_status (user_id, analysis_status),
    INDEX idx_resume_analyses_user_score (user_id, overall_score)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
