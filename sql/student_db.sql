-- ===================================================================
-- Script khởi tạo cơ sở dữ liệu student_db cho dự án DatabaseSync
-- Chạy script này trên cả Máy A và Máy B qua phpMyAdmin hoặc MySQL CLI
-- ===================================================================

CREATE DATABASE IF NOT EXISTS `student_db` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `student_db`;

-- 1. Bảng lưu trữ thông tin sinh viên
DROP TABLE IF EXISTS `students`;
CREATE TABLE `students` (
    `id` INT NOT NULL,
    `name` VARCHAR(100) NOT NULL,
    `email` VARCHAR(100),
    `major` VARCHAR(100),
    `updated_at` BIGINT NOT NULL,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. Bảng lưu nhật ký các thay đổi phát sinh tại máy này cần gửi đi (Outgoing Change Log)
-- Lưu ý: Chỉ các thao tác trực tiếp của người dùng cục bộ mới được thêm vào đây.
DROP TABLE IF EXISTS `outgoing_events`;
CREATE TABLE `outgoing_events` (
    `event_id` VARCHAR(36) NOT NULL,
    `student_id` INT NOT NULL,
    `operation` VARCHAR(10) NOT NULL, -- 'INSERT', 'UPDATE', 'DELETE'
    `payload` TEXT,                   -- JSON chứa dữ liệu sinh viên
    `created_at` BIGINT NOT NULL,
    `is_sent` TINYINT(1) DEFAULT 0,   -- 0: chưa nhận ACK, 1: đã gửi thành công
    PRIMARY KEY (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Bảng ghi nhận các eventId đã nhận từ máy đối tác (Received Events Tracker)
-- Dùng để chống lặp và chống trùng lặp sự kiện (Idempotency)
DROP TABLE IF EXISTS `received_events`;
CREATE TABLE `received_events` (
    `event_id` VARCHAR(36) NOT NULL,
    `received_at` BIGINT NOT NULL,
    PRIMARY KEY (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
