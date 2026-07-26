-- ============================================================
-- Dark Room Library bootstrap SQL
-- Database: dark_room_library
--
-- Usage:
--   mysql --default-character-set=utf8mb4 -u root -p < sql/init-dark-room-library.sql
--
-- Notes:
--   1. This script is for a fresh empty database.
--   2. Back up existing data before running it on an old environment.
--   3. Default super-admin account is for local demo only; keep its password private.
-- ============================================================

SET NAMES utf8mb4;

CREATE DATABASE IF NOT EXISTS `dark_room_library`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

USE `dark_room_library`;

SET FOREIGN_KEY_CHECKS = 0;

-- ============================================================
-- 1. user
-- user_role: 0=super admin, 1=admin, 2=reader, 3=purchaser, 4=logistics
-- is_coordinator_admin: 0=normal admin, 1=coordinator admin; valid only when user_role=1
-- account_status: 0=normal, 1=frozen, 2=cancelled
-- is_login: 0=enabled, 1=disabled
-- is_word: 0=normal, 1=muted
-- ============================================================
CREATE TABLE IF NOT EXISTS `user` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT 'user id',
  `user_account` varchar(50) NOT NULL COMMENT 'account',
  `user_name` varchar(50) NOT NULL COMMENT 'display name',
  `user_pwd` varchar(255) NOT NULL COMMENT 'password bcrypt hash',
  `user_avatar` varchar(500) DEFAULT NULL COMMENT 'avatar url',
  `user_email` varchar(100) DEFAULT NULL COMMENT 'email',
  `user_role` int NOT NULL DEFAULT 2 COMMENT 'role',
  `is_coordinator_admin` tinyint NOT NULL DEFAULT 0 COMMENT 'coordinator admin flag',
  `account_status` tinyint NOT NULL DEFAULT 0 COMMENT 'account status: 0 normal, 1 frozen, 2 cancelled',
  `is_login` tinyint NOT NULL DEFAULT 0 COMMENT 'login disabled flag',
  `is_word` tinyint NOT NULL DEFAULT 0 COMMENT 'muted flag',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_account` (`user_account`),
  UNIQUE KEY `uk_user_name` (`user_name`),
  KEY `idx_user_role` (`user_role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='user';

-- ============================================================
-- 2. category
-- ============================================================
CREATE TABLE IF NOT EXISTS `category` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT 'category id',
  `name` varchar(50) NOT NULL COMMENT 'category name',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='book category';

-- ============================================================
-- 3. bookshelf
-- ============================================================
CREATE TABLE IF NOT EXISTS `bookshelf` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT 'bookshelf id',
  `name` varchar(50) NOT NULL COMMENT 'bookshelf name',
  `location` varchar(100) DEFAULT NULL COMMENT 'location',
  `capacity` int NOT NULL DEFAULT 100 COMMENT 'capacity',
  `description` varchar(500) DEFAULT NULL COMMENT 'description',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='bookshelf';

-- ============================================================
-- 4. book
-- ============================================================
CREATE TABLE IF NOT EXISTS `book` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT 'book id',
  `name` varchar(255) NOT NULL COMMENT 'book name',
  `author` varchar(100) DEFAULT NULL COMMENT 'author',
  `isbn` varchar(30) DEFAULT NULL COMMENT 'isbn',
  `publisher` varchar(100) DEFAULT NULL COMMENT 'publisher',
  `category` varchar(50) DEFAULT NULL COMMENT 'category name',
  `total_count` int NOT NULL DEFAULT 0 COMMENT 'total stock',
  `available_count` int NOT NULL DEFAULT 0 COMMENT 'available stock',
  `cover` varchar(500) DEFAULT NULL COMMENT 'cover url',
  `description` varchar(2000) DEFAULT NULL COMMENT 'description',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT 'soft delete flag',
  `bookshelf_id` int unsigned DEFAULT NULL COMMENT 'bookshelf id',
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category`),
  KEY `idx_is_deleted` (`is_deleted`),
  KEY `idx_name` (`name`),
  KEY `idx_bookshelf_id` (`bookshelf_id`),
  CONSTRAINT `fk_book_bookshelf`
    FOREIGN KEY (`bookshelf_id`) REFERENCES `bookshelf` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `chk_book_stock`
    CHECK (`total_count` >= 0 AND `available_count` >= 0 AND `available_count` <= `total_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='book';

-- ============================================================
-- 5. borrow_record
-- status: 0=borrowing, 1=returned
-- ============================================================
CREATE TABLE IF NOT EXISTS `borrow_record` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT 'borrow record id',
  `user_id` int unsigned NOT NULL COMMENT 'user id',
  `book_id` int unsigned NOT NULL COMMENT 'book id',
  `borrow_time` datetime NOT NULL COMMENT 'borrow time',
  `due_date` datetime DEFAULT NULL COMMENT 'due date',
  `return_time` datetime DEFAULT NULL COMMENT 'return time',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT 'borrow status',
  `fine_amount` decimal(10,2) NOT NULL DEFAULT 0.00 COMMENT 'fine amount',
  `renew_count` int NOT NULL DEFAULT 0 COMMENT 'renew count',
  `due_reminder_sent_time` datetime DEFAULT NULL COMMENT 'due reminder sent time',
  `active_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `status` = 0 THEN 1 ELSE NULL END) STORED COMMENT 'active borrow flag',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_book_id` (`book_id`),
  KEY `idx_status` (`status`),
  KEY `idx_due_date` (`due_date`),
  KEY `idx_due_reminder` (`due_reminder_sent_time`),
  UNIQUE KEY `uk_borrow_active` (`user_id`, `book_id`, `active_flag`),
  CONSTRAINT `fk_borrow_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_borrow_book`
    FOREIGN KEY (`book_id`) REFERENCES `book` (`id`)
    ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='borrow record';

-- ============================================================
-- 6. notice
-- ============================================================
CREATE TABLE IF NOT EXISTS `notice` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT 'notice id',
  `name` varchar(100) DEFAULT NULL COMMENT 'title',
  `content` longtext COMMENT 'content',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='notice';

-- ============================================================
-- 7. book_review
-- ============================================================
CREATE TABLE IF NOT EXISTS `book_review` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT 'review id',
  `user_id` int unsigned NOT NULL COMMENT 'user id',
  `book_id` int unsigned NOT NULL COMMENT 'book id',
  `rating` tinyint unsigned NOT NULL DEFAULT 5 COMMENT 'rating 1-5',
  `content` longtext COMMENT 'review content',
  `status` tinyint unsigned NOT NULL DEFAULT 0 COMMENT '0=normal,1=hidden',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_book_id` (`book_id`),
  KEY `idx_status` (`status`),
  CONSTRAINT `fk_review_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_review_book`
    FOREIGN KEY (`book_id`) REFERENCES `book` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='book review';

-- ============================================================
-- 8. book_review_like
-- One user can like the same review only once.
-- ============================================================
CREATE TABLE IF NOT EXISTS `book_review_like` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT 'like id',
  `review_id` int unsigned NOT NULL COMMENT 'review id',
  `user_id` int unsigned NOT NULL COMMENT 'user id',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_user` (`review_id`, `user_id`),
  KEY `idx_user_id` (`user_id`),
  CONSTRAINT `fk_review_like_review`
    FOREIGN KEY (`review_id`) REFERENCES `book_review` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_review_like_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='book review like';

-- ============================================================
-- 9. book_review_reply
-- First-level replies for reader interaction around reviews.
-- ============================================================
CREATE TABLE IF NOT EXISTS `book_review_reply` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT 'reply id',
  `review_id` int unsigned NOT NULL COMMENT 'review id',
  `user_id` int unsigned NOT NULL COMMENT 'reply user id',
  `reply_to_user_id` int unsigned DEFAULT NULL COMMENT 'target user id',
  `content` longtext NOT NULL COMMENT 'reply content',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  PRIMARY KEY (`id`),
  KEY `idx_review_id` (`review_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_reply_to_user_id` (`reply_to_user_id`),
  CONSTRAINT `fk_review_reply_review`
    FOREIGN KEY (`review_id`) REFERENCES `book_review` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_review_reply_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_review_reply_to_user`
    FOREIGN KEY (`reply_to_user_id`) REFERENCES `user` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='book review reply';

-- ============================================================
-- 10. book_review_report
-- Reader reports are stored for admin audit instead of changing review visibility directly.
-- status: 0=pending, 1=handled, 2=ignored
-- ============================================================
CREATE TABLE IF NOT EXISTS `book_review_report` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT 'report id',
  `review_id` int unsigned NOT NULL COMMENT 'review id',
  `user_id` int unsigned NOT NULL COMMENT 'report user id',
  `reason` varchar(200) NOT NULL COMMENT 'report reason',
  `status` tinyint unsigned NOT NULL DEFAULT 0 COMMENT '0=pending,1=handled,2=ignored',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  `handle_time` datetime DEFAULT NULL COMMENT 'handled time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_review_report_user` (`review_id`, `user_id`),
  KEY `idx_status` (`status`),
  KEY `idx_user_id` (`user_id`),
  CONSTRAINT `fk_review_report_review`
    FOREIGN KEY (`review_id`) REFERENCES `book_review` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_review_report_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='book review report';

-- ============================================================
-- 11. book_favorite
-- ============================================================
CREATE TABLE IF NOT EXISTS `book_favorite` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT 'favorite id',
  `user_id` int unsigned NOT NULL COMMENT 'user id',
  `book_id` int unsigned NOT NULL COMMENT 'book id',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_book` (`user_id`, `book_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_book_id` (`book_id`),
  CONSTRAINT `fk_favorite_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_favorite_book`
    FOREIGN KEY (`book_id`) REFERENCES `book` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='book favorite';

-- ============================================================
-- 12. book_reservation
-- status: 0=waiting, 1=borrowed, 2=canceled, 3=notified, 4=expired
-- active_flag keeps one active reservation per user/book for status 0/3.
-- ============================================================
CREATE TABLE IF NOT EXISTS `book_reservation` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT 'reservation id',
  `user_id` int unsigned NOT NULL COMMENT 'user id',
  `book_id` int unsigned NOT NULL COMMENT 'book id',
  `reserve_time` datetime NOT NULL COMMENT 'reserved time',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT 'reservation status',
  `notify_time` datetime DEFAULT NULL COMMENT 'notified time',
  `active_flag` tinyint GENERATED ALWAYS AS (CASE WHEN `status` IN (0, 3) THEN 1 ELSE NULL END) STORED COMMENT 'active reservation flag',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_book_id` (`book_id`),
  KEY `idx_status` (`status`),
  KEY `idx_book_status_time` (`book_id`, `status`, `reserve_time`),
  UNIQUE KEY `uk_reservation_active` (`user_id`, `book_id`, `active_flag`),
  CONSTRAINT `fk_reservation_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_reservation_book`
    FOREIGN KEY (`book_id`) REFERENCES `book` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='book reservation';

-- ============================================================
-- 13. message_board
-- Attachments are stored as uploaded file metadata.
-- ============================================================
CREATE TABLE IF NOT EXISTS `message_board` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT 'message id',
  `user_id` int unsigned NOT NULL COMMENT 'user id',
  `content` longtext NOT NULL COMMENT 'content',
  `attachment_url` varchar(500) DEFAULT NULL COMMENT 'attachment url',
  `attachment_name` varchar(255) DEFAULT NULL COMMENT 'attachment original name',
  `attachment_type` varchar(50) DEFAULT NULL COMMENT 'attachment type',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  `reply` longtext COMMENT 'admin reply',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`),
  CONSTRAINT `fk_message_user`
    FOREIGN KEY (`user_id`) REFERENCES `user` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='message board';

-- ============================================================
-- 14. operation_log
-- No foreign key here, so audit history survives user deletion.
-- ============================================================
CREATE TABLE IF NOT EXISTS `operation_log` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT 'log id',
  `user_id` int unsigned DEFAULT NULL COMMENT 'user id',
  `user_name` varchar(50) DEFAULT NULL COMMENT 'user name',
  `operation` varchar(100) NOT NULL COMMENT 'operation',
  `target` varchar(255) DEFAULT NULL COMMENT 'target',
  `detail` varchar(1000) DEFAULT NULL COMMENT 'detail',
  `ip` varchar(50) DEFAULT NULL COMMENT 'ip address',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_operation` (`operation`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='operation log';

-- ============================================================
-- 15. notification_task
-- status: 0=pending, 1=sent, 2=failed, 3=processing
-- ============================================================
CREATE TABLE IF NOT EXISTS `notification_task` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT 'notification task id',
  `receiver_email` varchar(255) NOT NULL COMMENT 'receiver email',
  `subject` varchar(255) NOT NULL COMMENT 'subject',
  `content` text NOT NULL COMMENT 'content',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT 'status',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT 'retry count',
  `last_error` varchar(500) DEFAULT NULL COMMENT 'last error',
  `next_retry_time` datetime DEFAULT NULL COMMENT 'next retry time',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  PRIMARY KEY (`id`),
  KEY `idx_status_retry` (`status`, `next_retry_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='notification task';

-- ============================================================
-- 16. procurement_order
-- status: 0=pending, 1=purchasing, 2=ordered, 3=shipped, 4=arrived, 5=warehoused, 6=completed, 7=canceled
-- ============================================================
CREATE TABLE IF NOT EXISTS `procurement_order` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT 'procurement order id',
  `book_id` int unsigned NOT NULL COMMENT 'book id',
  `book_name` varchar(255) NOT NULL COMMENT 'book name snapshot',
  `isbn` varchar(30) DEFAULT NULL COMMENT 'isbn snapshot',
  `category` varchar(50) DEFAULT NULL COMMENT 'category snapshot',
  `request_count` int NOT NULL COMMENT 'requested count',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT 'procurement status',
  `requester_id` int unsigned DEFAULT NULL COMMENT 'admin requester id',
  `purchaser_id` int unsigned DEFAULT NULL COMMENT 'purchaser id',
  `logistics_id` int unsigned DEFAULT NULL COMMENT 'logistics user id',
  `request_note` varchar(1000) DEFAULT NULL COMMENT 'request note',
  `purchase_note` varchar(1000) DEFAULT NULL COMMENT 'purchase note',
  `stock_applied` tinyint NOT NULL DEFAULT 0 COMMENT 'whether stock has been applied',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  `order_time` datetime DEFAULT NULL COMMENT 'ordered time',
  `shipped_time` datetime DEFAULT NULL COMMENT 'shipped time',
  `arrival_time` datetime DEFAULT NULL COMMENT 'arrival time',
  `completed_time` datetime DEFAULT NULL COMMENT 'completed time',
  PRIMARY KEY (`id`),
  KEY `idx_book_id` (`book_id`),
  KEY `idx_status` (`status`),
  KEY `idx_requester_id` (`requester_id`),
  KEY `idx_purchaser_id` (`purchaser_id`),
  KEY `idx_logistics_id` (`logistics_id`),
  CONSTRAINT `fk_procurement_order_book`
    FOREIGN KEY (`book_id`) REFERENCES `book` (`id`)
    ON DELETE RESTRICT ON UPDATE CASCADE,
  CONSTRAINT `fk_procurement_order_requester`
    FOREIGN KEY (`requester_id`) REFERENCES `user` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_procurement_order_purchaser`
    FOREIGN KEY (`purchaser_id`) REFERENCES `user` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE,
  CONSTRAINT `fk_procurement_order_logistics`
    FOREIGN KEY (`logistics_id`) REFERENCES `user` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='procurement order';

-- ============================================================
-- 17. procurement_logistics
-- status: 0=pending, 1=in transit, 2=arrived, 3=warehoused
-- ============================================================
CREATE TABLE IF NOT EXISTS `procurement_logistics` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT 'procurement logistics id',
  `order_id` int unsigned NOT NULL COMMENT 'procurement order id',
  `logistics_user_id` int unsigned DEFAULT NULL COMMENT 'logistics user id',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT 'logistics status',
  `tracking_no` varchar(100) DEFAULT NULL COMMENT 'tracking number',
  `carrier` varchar(100) DEFAULT NULL COMMENT 'carrier',
  `remark` varchar(1000) DEFAULT NULL COMMENT 'remark',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'updated time',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_procurement_logistics_order` (`order_id`),
  KEY `idx_logistics_user_id` (`logistics_user_id`),
  KEY `idx_status` (`status`),
  CONSTRAINT `fk_procurement_logistics_order`
    FOREIGN KEY (`order_id`) REFERENCES `procurement_order` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_procurement_logistics_user`
    FOREIGN KEY (`logistics_user_id`) REFERENCES `user` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='procurement logistics';

-- ============================================================
-- 18. procurement_message
-- channel_type: 0=admin-purchaser, 1=purchaser-logistics
-- read_status: 0=unread, 1=read
-- ============================================================
CREATE TABLE IF NOT EXISTS `procurement_message` (
  `id` int unsigned NOT NULL AUTO_INCREMENT COMMENT 'procurement message id',
  `order_id` int unsigned NOT NULL COMMENT 'procurement order id',
  `channel_type` tinyint NOT NULL COMMENT 'message channel type',
  `sender_id` int unsigned NOT NULL COMMENT 'sender user id',
  `receiver_id` int unsigned NOT NULL COMMENT 'receiver user id',
  `content` text NOT NULL COMMENT 'message content',
  `read_status` tinyint NOT NULL DEFAULT 0 COMMENT 'read status',
  `read_time` datetime DEFAULT NULL COMMENT 'read time',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT 'created time',
  PRIMARY KEY (`id`),
  KEY `idx_order_channel` (`order_id`, `channel_type`),
  KEY `idx_receiver_read` (`receiver_id`, `read_status`),
  KEY `idx_sender_id` (`sender_id`),
  CONSTRAINT `fk_procurement_message_order`
    FOREIGN KEY (`order_id`) REFERENCES `procurement_order` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_procurement_message_sender`
    FOREIGN KEY (`sender_id`) REFERENCES `user` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE,
  CONSTRAINT `fk_procurement_message_receiver`
    FOREIGN KEY (`receiver_id`) REFERENCES `user` (`id`)
    ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='procurement message';

-- ============================================================
-- 19. stored_file
-- status: 0=temporary, 1=bound, 2=delete pending
-- ref_type: book_cover/user_avatar/msg_attachment/notice_asset
-- ============================================================
CREATE TABLE IF NOT EXISTS `stored_file` (
  `file_name` varchar(64) NOT NULL COMMENT 'generated storage file name',
  `original_name` varchar(255) NOT NULL COMMENT 'original upload file name',
  `extension` varchar(10) NOT NULL COMMENT 'file extension without dot',
  `content_type` varchar(100) NOT NULL COMMENT 'normalized MIME type',
  `file_size` bigint unsigned NOT NULL COMMENT 'file size in bytes',
  `uploader_id` int unsigned DEFAULT NULL COMMENT 'upload user id',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 temporary, 1 bound, 2 delete pending',
  `ref_type` varchar(30) DEFAULT NULL COMMENT 'business reference type',
  `ref_id` int unsigned DEFAULT NULL COMMENT 'business reference id',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'upload time',
  `bind_time` datetime DEFAULT NULL COMMENT 'first bind time',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT 'update time',
  PRIMARY KEY (`file_name`),
  KEY `idx_status_create` (`status`, `create_time`),
  KEY `idx_reference` (`ref_type`, `ref_id`),
  KEY `idx_uploader_id` (`uploader_id`),
  CONSTRAINT `fk_stored_file_uploader`
    FOREIGN KEY (`uploader_id`) REFERENCES `user` (`id`)
    ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci COMMENT='managed uploaded file';

SET FOREIGN_KEY_CHECKS = 1;

-- ============================================================
-- 20. seed data
-- ============================================================

INSERT IGNORE INTO `user`
  (`user_account`, `user_name`, `user_pwd`, `user_avatar`, `user_email`, `user_role`, `is_coordinator_admin`, `account_status`, `is_login`, `is_word`, `create_time`)
VALUES
  ('drl_root_aurora', '暗室总馆员', '$2a$10$L1Tf7a1p/l86HX2TbVE8nuahTuM/NerVSFgJEAWj40CdIhwKY.MwO', NULL, 'drl_root_aurora@darkroomlibrary.local', 0, 0, 0, 0, 0, NOW());

INSERT IGNORE INTO `category` (`name`, `create_time`) VALUES
  ('编程', NOW()),
  ('计算机基础', NOW()),
  ('算法', NOW()),
  ('人工智能', NOW()),
  ('数据库', NOW()),
  ('文学', NOW()),
  ('历史', NOW()),
  ('科学', NOW()),
  ('哲学', NOW()),
  ('艺术', NOW()),
  ('经济', NOW());

INSERT IGNORE INTO `bookshelf` (`name`, `location`, `capacity`, `description`, `create_time`) VALUES
  ('暗室总架', '总馆·雾灯厅', 100, '系统初始化主书架', NOW());

-- ============================================================
-- End
-- ============================================================
