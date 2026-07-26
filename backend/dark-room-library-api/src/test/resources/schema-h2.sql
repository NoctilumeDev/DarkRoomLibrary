-- H2 测试数据库表结构（MySQL 兼容模式）
-- 用于单元测试，自动初始化

CREATE TABLE IF NOT EXISTS `user` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `user_account` VARCHAR(50) NOT NULL,
    `user_name` VARCHAR(50) NOT NULL,
    `user_pwd` VARCHAR(255) NOT NULL,
    `user_avatar` VARCHAR(500) DEFAULT '',
    `user_email` VARCHAR(100) DEFAULT '',
    `user_role` INT DEFAULT 2,
    `is_coordinator_admin` TINYINT DEFAULT 0,
    `account_status` TINYINT DEFAULT 0,
    `is_login` TINYINT DEFAULT 0,
    `is_word` TINYINT DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_account` (`user_account`),
    UNIQUE KEY `uk_user_name` (`user_name`),
    KEY `idx_user_role` (`user_role`)
);

CREATE TABLE IF NOT EXISTS `book` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(100) NOT NULL,
    `author` VARCHAR(100) NOT NULL,
    `isbn` VARCHAR(30) DEFAULT '',
    `publisher` VARCHAR(100) DEFAULT '',
    `category` VARCHAR(50) DEFAULT '',
    `total_count` INT DEFAULT 0,
    `available_count` INT DEFAULT 0,
    `cover` VARCHAR(500) DEFAULT '',
    `description` TEXT,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `is_deleted` TINYINT DEFAULT 0,
    `bookshelf_id` INT DEFAULT NULL,
    CONSTRAINT `chk_book_stock`
      CHECK (`total_count` >= 0 AND `available_count` >= 0 AND `available_count` <= `total_count`)
);

CREATE TABLE IF NOT EXISTS `borrow_record` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `user_id` INT NOT NULL,
    `book_id` INT NOT NULL,
    `borrow_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `due_date` DATETIME,
    `return_time` DATETIME,
    `status` TINYINT DEFAULT 0,
    `fine_amount` DECIMAL(10,2) DEFAULT 0,
    `renew_count` INT DEFAULT 0,
    `due_reminder_sent_time` DATETIME,
    `active_flag` TINYINT GENERATED ALWAYS AS (CASE WHEN `status` = 0 THEN 1 ELSE NULL END),
    UNIQUE KEY `uk_borrow_active` (`user_id`, `book_id`, `active_flag`)
);

CREATE TABLE IF NOT EXISTS `category` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(50) NOT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_category_name` (`name`)
);

CREATE TABLE IF NOT EXISTS `book_review` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `user_id` INT NOT NULL,
    `book_id` INT NOT NULL,
    `rating` TINYINT DEFAULT 5,
    `content` TEXT,
    `status` TINYINT DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS `book_review_like` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `review_id` INT NOT NULL,
    `user_id` INT NOT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_review_user` (`review_id`, `user_id`)
);

CREATE TABLE IF NOT EXISTS `book_review_reply` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `review_id` INT NOT NULL,
    `user_id` INT NOT NULL,
    `reply_to_user_id` INT,
    `content` TEXT NOT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS `book_review_report` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `review_id` INT NOT NULL,
    `user_id` INT NOT NULL,
    `reason` VARCHAR(200) NOT NULL,
    `status` TINYINT DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `handle_time` DATETIME,
    UNIQUE KEY `uk_review_report_user` (`review_id`, `user_id`)
);

CREATE TABLE IF NOT EXISTS `message_board` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `user_id` INT NOT NULL,
    `content` TEXT NOT NULL,
    `attachment_url` VARCHAR(500),
    `attachment_name` VARCHAR(255),
    `attachment_type` VARCHAR(50),
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `reply` TEXT
);

CREATE TABLE IF NOT EXISTS `book_reservation` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `user_id` INT NOT NULL,
    `book_id` INT NOT NULL,
    `reserve_time` DATETIME NOT NULL,
    `status` TINYINT DEFAULT 0,
    `notify_time` DATETIME,
    `active_flag` TINYINT GENERATED ALWAYS AS (CASE WHEN `status` IN (0, 3) THEN 1 ELSE NULL END),
    UNIQUE KEY `uk_reservation_active` (`user_id`, `book_id`, `active_flag`)
);

CREATE TABLE IF NOT EXISTS `book_favorite` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `user_id` INT NOT NULL,
    `book_id` INT NOT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_user_book` (`user_id`, `book_id`)
);

CREATE TABLE IF NOT EXISTS `notification_task` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `receiver_email` VARCHAR(255) NOT NULL,
    `subject` VARCHAR(255) NOT NULL,
    `content` TEXT NOT NULL,
    `status` TINYINT DEFAULT 0,
    `retry_count` INT DEFAULT 0,
    `last_error` VARCHAR(500),
    `next_retry_time` DATETIME,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS `operation_log` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `user_id` INT,
    `user_name` VARCHAR(50),
    `operation` VARCHAR(100) NOT NULL,
    `target` VARCHAR(255),
    `detail` VARCHAR(1000),
    `ip` VARCHAR(50),
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS `notice` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(100) NOT NULL,
    `content` TEXT NOT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS `bookshelf` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `name` VARCHAR(50) NOT NULL,
    `location` VARCHAR(100) DEFAULT NULL,
    `capacity` INT DEFAULT 100,
    `description` VARCHAR(500) DEFAULT NULL,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS `procurement_order` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `book_id` INT NOT NULL,
    `book_name` VARCHAR(255) NOT NULL,
    `isbn` VARCHAR(30),
    `category` VARCHAR(50),
    `request_count` INT NOT NULL,
    `status` TINYINT DEFAULT 0,
    `requester_id` INT,
    `purchaser_id` INT,
    `logistics_id` INT,
    `request_note` VARCHAR(1000),
    `purchase_note` VARCHAR(1000),
    `stock_applied` TINYINT DEFAULT 0,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `order_time` DATETIME,
    `shipped_time` DATETIME,
    `arrival_time` DATETIME,
    `completed_time` DATETIME
);

CREATE TABLE IF NOT EXISTS `procurement_logistics` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `order_id` INT NOT NULL,
    `logistics_user_id` INT,
    `status` TINYINT DEFAULT 0,
    `tracking_no` VARCHAR(100),
    `carrier` VARCHAR(100),
    `remark` VARCHAR(1000),
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_procurement_logistics_order` (`order_id`)
);

CREATE TABLE IF NOT EXISTS `procurement_message` (
    `id` INT AUTO_INCREMENT PRIMARY KEY,
    `order_id` INT NOT NULL,
    `channel_type` TINYINT NOT NULL,
    `sender_id` INT NOT NULL,
    `receiver_id` INT NOT NULL,
    `content` TEXT NOT NULL,
    `read_status` TINYINT DEFAULT 0,
    `read_time` DATETIME,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS `stored_file` (
    `file_name` VARCHAR(64) PRIMARY KEY,
    `original_name` VARCHAR(255) NOT NULL,
    `extension` VARCHAR(10) NOT NULL,
    `content_type` VARCHAR(100) NOT NULL,
    `file_size` BIGINT NOT NULL,
    `uploader_id` INT,
    `status` TINYINT DEFAULT 0,
    `ref_type` VARCHAR(30),
    `ref_id` INT,
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `bind_time` DATETIME,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP
);
