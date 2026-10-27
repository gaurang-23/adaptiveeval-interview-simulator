-- V1__init_schema.sql
-- MySQL 8 Normalized Schema for Adaptive Technical Interview Simulator

CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(190) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    target_role VARCHAR(120) NOT NULL DEFAULT 'Software Engineer',
    experience_level VARCHAR(60) NOT NULL DEFAULT 'Mid-Level',
    preferred_language VARCHAR(40) NOT NULL DEFAULT 'JAVA',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE user_skill_profiles (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    competency VARCHAR(60) NOT NULL,
    score DOUBLE NOT NULL DEFAULT 50.0,
    confidence DOUBLE NOT NULL DEFAULT 0.0,
    assessments_count INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_skill_profile_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    UNIQUE KEY uk_user_competency (user_id, competency)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    slug VARCHAR(160) NOT NULL UNIQUE,
    topic VARCHAR(80) NOT NULL,
    difficulty VARCHAR(30) NOT NULL,
    question_type VARCHAR(40) NOT NULL DEFAULT 'DSA',
    entry_method_name VARCHAR(100) NOT NULL,
    statement TEXT NOT NULL,
    constraints_text TEXT NOT NULL,
    examples_json JSON NOT NULL,
    starter_code TEXT NOT NULL,
    reference_solution TEXT NOT NULL,
    rubric_json JSON NOT NULL,
    expected_concepts_json JSON NOT NULL,
    hints_json JSON NOT NULL,
    expected_time_complexity VARCHAR(60) NOT NULL,
    expected_space_complexity VARCHAR(60) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_questions_topic_diff (topic, difficulty, active),
    INDEX idx_questions_type (question_type, active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE question_test_cases (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    question_id BIGINT NOT NULL,
    input_data TEXT NOT NULL,
    expected_output TEXT NOT NULL,
    is_sample BOOLEAN NOT NULL DEFAULT FALSE,
    order_index INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_test_cases_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE,
    INDEX idx_test_cases_question (question_id, is_sample, order_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE interview_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    interview_type VARCHAR(40) NOT NULL,
    configured_difficulty VARCHAR(30) NOT NULL,
    interview_style VARCHAR(40) NOT NULL DEFAULT 'INTERVIEWER_LED',
    topics_json JSON NOT NULL,
    question_count INT NOT NULL DEFAULT 3,
    duration_seconds INT NOT NULL DEFAULT 1800,
    started_at TIMESTAMP NULL,
    deadline_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    status VARCHAR(40) NOT NULL DEFAULT 'CREATED',
    overall_score DOUBLE NULL,
    report_json JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sessions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_sessions_user_status (user_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE interview_questions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    sequence_number INT NOT NULL,
    selection_reason VARCHAR(500) NOT NULL,
    target_competency VARCHAR(60) NOT NULL DEFAULT 'PATTERN_RECOGNITION',
    status VARCHAR(40) NOT NULL DEFAULT 'IN_PROGRESS',
    hints_used INT NOT NULL DEFAULT 0,
    explanation_submitted BOOLEAN NOT NULL DEFAULT FALSE,
    started_at TIMESTAMP NULL,
    completed_at TIMESTAMP NULL,
    CONSTRAINT fk_iq_session FOREIGN KEY (session_id) REFERENCES interview_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_iq_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE RESTRICT,
    UNIQUE KEY uk_session_seq (session_id, sequence_number),
    INDEX idx_iq_session_status (session_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE attempts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    submitted_code TEXT NOT NULL,
    language VARCHAR(30) NOT NULL DEFAULT 'JAVA',
    is_final_submission BOOLEAN NOT NULL DEFAULT FALSE,
    compilation_status VARCHAR(40) NOT NULL,
    compiler_output TEXT NULL,
    tests_passed INT NOT NULL DEFAULT 0,
    tests_total INT NOT NULL DEFAULT 0,
    runtime_ms BIGINT NOT NULL DEFAULT 0,
    memory_kb BIGINT NOT NULL DEFAULT 0,
    failure_category VARCHAR(60) NOT NULL DEFAULT 'NONE',
    test_results_json JSON NULL,
    submitted_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_attempts_session FOREIGN KEY (session_id) REFERENCES interview_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_attempts_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE RESTRICT,
    INDEX idx_attempts_session_question (session_id, question_id, submitted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE skill_assessments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    question_id BIGINT NULL,
    competency VARCHAR(60) NOT NULL,
    score DOUBLE NOT NULL,
    previous_score DOUBLE NULL,
    confidence DOUBLE NOT NULL,
    evidence_json JSON NOT NULL,
    assessed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_sa_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_sa_session FOREIGN KEY (session_id) REFERENCES interview_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_sa_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE SET NULL,
    INDEX idx_sa_user_competency (user_id, competency, assessed_at),
    INDEX idx_sa_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE feedback (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    attempt_id BIGINT NULL,
    session_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    category VARCHAR(60) NOT NULL,
    score DOUBLE NOT NULL,
    confidence DOUBLE NOT NULL,
    evidence TEXT NOT NULL,
    recommendation TEXT NOT NULL,
    source_type VARCHAR(40) NOT NULL DEFAULT 'DETERMINISTIC',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_feedback_attempt FOREIGN KEY (attempt_id) REFERENCES attempts(id) ON DELETE SET NULL,
    CONSTRAINT fk_feedback_session FOREIGN KEY (session_id) REFERENCES interview_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_feedback_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE RESTRICT,
    INDEX idx_feedback_session (session_id, question_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE question_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    event_type VARCHAR(60) NOT NULL,
    event_payload_json JSON NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_qe_session FOREIGN KEY (session_id) REFERENCES interview_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_qe_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE RESTRICT,
    INDEX idx_qe_session_question (session_id, question_id, event_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE interview_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    question_id BIGINT NULL,
    role VARCHAR(30) NOT NULL,
    content TEXT NOT NULL,
    message_type VARCHAR(40) NOT NULL DEFAULT 'CHAT',
    metadata_json JSON NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_im_session FOREIGN KEY (session_id) REFERENCES interview_sessions(id) ON DELETE CASCADE,
    CONSTRAINT fk_im_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE SET NULL,
    INDEX idx_im_session (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE practice_recommendations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    target_competency VARCHAR(60) NOT NULL,
    suggested_difficulty VARCHAR(30) NOT NULL,
    topic VARCHAR(80) NOT NULL,
    reason TEXT NOT NULL,
    learning_objective TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pr_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_pr_question FOREIGN KEY (question_id) REFERENCES questions(id) ON DELETE CASCADE,
    INDEX idx_pr_user_status (user_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
