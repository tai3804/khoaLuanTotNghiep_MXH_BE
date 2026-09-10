-- Script khởi tạo các database cho các microservices trong hệ thống KLTN
SELECT 'CREATE DATABASE auth_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'auth_db')\gexec
SELECT 'CREATE DATABASE user_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'user_db')\gexec
SELECT 'CREATE DATABASE post_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'post_db')\gexec
SELECT 'CREATE DATABASE chat_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'chat_db')\gexec
SELECT 'CREATE DATABASE call_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'call_db')\gexec
SELECT 'CREATE DATABASE feed_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'feed_db')\gexec
SELECT 'CREATE DATABASE moderation_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'moderation_db')\gexec
SELECT 'CREATE DATABASE admin_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'admin_db')\gexec
SELECT 'CREATE DATABASE ai_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'ai_db')\gexec
SELECT 'CREATE DATABASE notification_db' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'notification_db')\gexec
