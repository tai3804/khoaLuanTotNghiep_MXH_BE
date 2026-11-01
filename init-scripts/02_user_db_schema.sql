-- ===================================================================
-- Bảng và dữ liệu mẫu cho user_db
-- ===================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. User Profiles
CREATE TABLE IF NOT EXISTS user_profiles (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL UNIQUE,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(50) NOT NULL,
    middle_name VARCHAR(50),
    avatar_url VARCHAR(500),
    cover_url VARCHAR(500),
    bio TEXT,
    date_of_birth DATE,
    gender VARCHAR(20),
    location VARCHAR(100),
    website VARCHAR(200),
    follower_count BIGINT NOT NULL DEFAULT 0,
    following_count BIGINT NOT NULL DEFAULT 0,
    friend_count BIGINT NOT NULL DEFAULT 0,
    is_online BOOLEAN DEFAULT FALSE,
    last_active_at TIMESTAMP,
    is_professional_mode BOOLEAN DEFAULT FALSE,
    creator_category VARCHAR(100),
    profile_view_count BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    is_deleted BOOLEAN DEFAULT FALSE
);

-- 2. User Connections (Bạn bè, Theo dõi)
CREATE TABLE IF NOT EXISTS user_connections (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    requester_id UUID NOT NULL,
    target_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    is_deleted BOOLEAN DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_conn_req_target_type ON user_connections (requester_id, target_id, type);
CREATE INDEX IF NOT EXISTS idx_conn_target_type_status ON user_connections (target_id, type, status);
CREATE INDEX IF NOT EXISTS idx_conn_req_type_status ON user_connections (requester_id, type, status);

-- 3. User Reputation
CREATE TABLE IF NOT EXISTS user_reputation (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL UNIQUE,
    score INT NOT NULL DEFAULT 100,
    recovery_anchor_at TIMESTAMP,
    locked_until TIMESTAMP,
    version BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    is_deleted BOOLEAN DEFAULT FALSE
);

-- 4. Reputation Events
CREATE TABLE IF NOT EXISTS reputation_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
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
    updated_by VARCHAR(255),
    is_deleted BOOLEAN DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_rep_event_user ON reputation_events (user_id);
CREATE INDEX IF NOT EXISTS idx_rep_event_source ON reputation_events (user_id, source_type, source_id);

-- 5. User Privacy Settings
CREATE TABLE IF NOT EXISTS user_privacy_settings (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL UNIQUE,
    default_post_privacy VARCHAR(30) DEFAULT 'PUBLIC',
    friend_request_privacy VARCHAR(30) DEFAULT 'EVERYONE',
    friend_list_privacy VARCHAR(30) DEFAULT 'PUBLIC',
    search_privacy VARCHAR(30) DEFAULT 'EVERYONE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    is_deleted BOOLEAN DEFAULT FALSE
);

-- 6. User Blocks
CREATE TABLE IF NOT EXISTS user_blocks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    blocker_id UUID NOT NULL,
    blocked_id UUID NOT NULL,
    reason VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    is_deleted BOOLEAN DEFAULT FALSE
);

-- 7. Groups
CREATE TABLE IF NOT EXISTS groups (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) NOT NULL,
    description VARCHAR(1000),
    cover_url VARCHAR(500),
    privacy VARCHAR(20) NOT NULL,
    creator_id UUID NOT NULL,
    member_count BIGINT NOT NULL DEFAULT 1,
    post_approval_required BOOLEAN NOT NULL DEFAULT FALSE,
    rules VARCHAR(3000),
    membership_questions VARCHAR(1000),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    is_deleted BOOLEAN DEFAULT FALSE
);

-- 8. Group Members
CREATE TABLE IF NOT EXISTS group_members (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    group_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reviewed_at TIMESTAMP,
    notification_level VARCHAR(20),
    invited_by UUID,
    join_answers TEXT,
    CONSTRAINT uk_group_member UNIQUE (group_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_group_member_group_status ON group_members (group_id, status);
CREATE INDEX IF NOT EXISTS idx_group_member_user_status ON group_members (user_id, status);

-- 9. Profile View Histories
CREATE TABLE IF NOT EXISTS profile_view_histories (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    target_user_id UUID NOT NULL,
    viewer_id UUID NOT NULL,
    last_viewed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255),
    is_deleted BOOLEAN DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_pvh_target_user ON profile_view_histories (target_user_id);
CREATE INDEX IF NOT EXISTS idx_pvh_viewer ON profile_view_histories (viewer_id);

-- ===================================================================
-- SEED DATA: User Profiles từ auth_db.users
-- ===================================================================

INSERT INTO user_profiles (user_id, first_name, last_name, middle_name)
VALUES 
  ('23d40e64-e145-4102-b144-350112e17e1f', 'Tài', 'Trần', 'Thanh'),
  ('3965e092-492f-48c0-a2da-2ec1fcb07c60', 'Điềm', 'Ngô', NULL),
  ('f4f93faa-c543-4cd4-a382-e71c7dece6aa', 'Ánh Ngọc', 'Nguyễn', NULL),
  ('d5b6f15b-d11f-4b07-8d7e-3047e87eaea8', 'Admin', 'Hệ Thống', NULL),
  ('4738a34e-f8f0-47de-9e8e-39bd6926df35', 'Kiểm Duyệt Viên', 'Hệ Thống', NULL)
ON CONFLICT (user_id) DO UPDATE 
SET first_name = EXCLUDED.first_name, last_name = EXCLUDED.last_name, middle_name = EXCLUDED.middle_name;

-- SEED DATA: User Reputation (100 điểm)
INSERT INTO user_reputation (user_id, score)
VALUES 
  ('23d40e64-e145-4102-b144-350112e17e1f', 100),
  ('3965e092-492f-48c0-a2da-2ec1fcb07c60', 100),
  ('f4f93faa-c543-4cd4-a382-e71c7dece6aa', 100),
  ('d5b6f15b-d11f-4b07-8d7e-3047e87eaea8', 100),
  ('4738a34e-f8f0-47de-9e8e-39bd6926df35', 100)
ON CONFLICT (user_id) DO NOTHING;

-- SEED DATA: User Privacy Settings
INSERT INTO user_privacy_settings (user_id)
VALUES 
  ('23d40e64-e145-4102-b144-350112e17e1f'),
  ('3965e092-492f-48c0-a2da-2ec1fcb07c60'),
  ('f4f93faa-c543-4cd4-a382-e71c7dece6aa'),
  ('d5b6f15b-d11f-4b07-8d7e-3047e87eaea8'),
  ('4738a34e-f8f0-47de-9e8e-39bd6926df35')
ON CONFLICT (user_id) DO NOTHING;

-- SEED DATA: Lời mời kết bạn đang chờ giữa Nguyễn Ánh Ngọc và Trần Tài
INSERT INTO user_connections (requester_id, target_id, type, status)
VALUES 
  ('f4f93faa-c543-4cd4-a382-e71c7dece6aa', '23d40e64-e145-4102-b144-350112e17e1f', 'FRIEND', 'PENDING'),
  ('f4f93faa-c543-4cd4-a382-e71c7dece6aa', '23d40e64-e145-4102-b144-350112e17e1f', 'FOLLOW', 'ACCEPTED');
