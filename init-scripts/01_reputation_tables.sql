-- ===================================================================
-- Script tạo bảng điểm uy tín (Reputation System) cho user_db
-- Chạy script này trên PostgreSQL của user_db (trên Render / Production)
-- nếu Hibernate ddl-auto chưa tự động tạo bảng.
-- ===================================================================

-- 1. Bảng lưu trữ điểm uy tín hiện tại của người dùng
CREATE TABLE IF NOT EXISTS user_reputation (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    score INT NOT NULL DEFAULT 100,
    recovery_anchor_at TIMESTAMP,
    locked_until TIMESTAMP,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    last_modified_by VARCHAR(255)
);

-- 2. Bảng lưu lịch sử biến động điểm uy tín (phạt, hoàn điểm, phục hồi hàng ngày)
CREATE TABLE IF NOT EXISTS reputation_events (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL,
    action VARCHAR(30),
    source_type VARCHAR(20),
    source_id UUID,
    points INT NOT NULL,
    score_before INT NOT NULL,
    score_after INT NOT NULL,
    reason VARCHAR(500),
    reverted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    last_modified_by VARCHAR(255)
);

-- Index tăng tốc truy vấn lịch sử và lọc vi phạm theo đối tượng
CREATE INDEX IF NOT EXISTS idx_rep_event_user ON reputation_events (user_id);
CREATE INDEX IF NOT EXISTS idx_rep_event_source ON reputation_events (user_id, source_type, source_id);
