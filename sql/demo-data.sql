-- ============================================================
-- Dark Room Library optional demo data
--
-- Run after init-dark-room-library.sql for local demonstrations.
-- These accounts and passwords are public sample credentials.
-- Delete them or change every password before an internet-facing deployment.
-- ============================================================

SET NAMES utf8mb4;
USE `dark_room_library`;

-- Demo identities use project-specific names and separate strong passwords.
INSERT IGNORE INTO `user`
  (`user_account`, `user_name`, `user_pwd`, `user_avatar`, `user_email`, `user_role`, `is_coordinator_admin`, `account_status`, `is_login`, `is_word`, `create_time`)
VALUES
  ('drl_keeper_qingwu', '守卷青梧', '$2a$10$WRhoA87NzZORIGHundu3rOJvKdLP0JOiuUM0ayA2bDV9jq0VifrX2', NULL, 'drl_keeper_qingwu@darkroomlibrary.local', 1, 1, 0, 0, 0, '2026-07-20 09:10:00'),
  ('drl_reader_yandeng', '砚灯拾页', '$2a$10$v8aupQ.WW2jNXX1OjaqksOs0PIkz7PLpJ4we4PrFJ40zox9IPrFsG', NULL, 'drl_reader_yandeng@darkroomlibrary.local', 2, 0, 0, 0, 0, '2026-07-20 09:20:00'),
  ('drl_reader_zhiyue', '纸月听澜', '$2a$10$v8aupQ.WW2jNXX1OjaqksOs0PIkz7PLpJ4we4PrFJ40zox9IPrFsG', NULL, 'drl_reader_zhiyue@darkroomlibrary.local', 2, 0, 1, 1, 0, '2026-07-20 09:25:00'),
  ('drl_buyer_xinglan', '采书星阑', '$2a$10$L2As1i8VTpsLUrcHv3JvuOk59kFia65rc2HYvqqChL0EDcXygiLIa', NULL, 'drl_buyer_xinglan@darkroomlibrary.local', 3, 0, 0, 0, 0, '2026-07-20 09:30:00'),
  ('drl_logistics_chenxiang', '归架沉香', '$2a$10$DWE7yocPtSmeU9oRknAXi.3rLlnSrtStvRHTCcUk7PmJdmNrMIvOy', NULL, 'drl_logistics_chenxiang@darkroomlibrary.local', 4, 0, 0, 0, 0, '2026-07-20 09:40:00');

-- Fictional catalogue records avoid copying real book metadata.
INSERT INTO `book`
  (`name`, `author`, `isbn`, `publisher`, `category`, `total_count`, `available_count`, `cover`, `description`, `create_time`, `is_deleted`, `bookshelf_id`)
SELECT
  '暗室藏书', '岑夜录', '9900000000001', '暗室藏书局', '文学', 6, 3, NULL,
  '记录一间夜间图书馆里，书与读者彼此抵达的六个片段。',
  '2026-07-20 10:00:00', 0, shelf.id
FROM `bookshelf` shelf
WHERE shelf.name = '暗室总架'
  AND NOT EXISTS (SELECT 1 FROM `book` WHERE `name` = '暗室藏书')
LIMIT 1;

INSERT INTO `book`
  (`name`, `author`, `isbn`, `publisher`, `category`, `total_count`, `available_count`, `cover`, `description`, `create_time`, `is_deleted`, `bookshelf_id`)
SELECT
  '雾灯索引', '江雾衡', '9900000000002', '雾桥文库', '历史', 4, 1, NULL,
  '从散落档案中重建一座旧城阅读史的索引札记。',
  '2026-07-20 10:05:00', 0, shelf.id
FROM `bookshelf` shelf
WHERE shelf.name = '暗室总架'
  AND NOT EXISTS (SELECT 1 FROM `book` WHERE `name` = '雾灯索引')
LIMIT 1;

INSERT INTO `book`
  (`name`, `author`, `isbn`, `publisher`, `category`, `total_count`, `available_count`, `cover`, `description`, `create_time`, `is_deleted`, `bookshelf_id`)
SELECT
  '归架之前', '闻归舟', '9900000000003', '归架书坊', '文学', 3, 0, NULL,
  '一本书在归架前经过的借阅、批注、等待与重逢。',
  '2026-07-20 10:10:00', 0, shelf.id
FROM `bookshelf` shelf
WHERE shelf.name = '暗室总架'
  AND NOT EXISTS (SELECT 1 FROM `book` WHERE `name` = '归架之前')
LIMIT 1;

INSERT INTO `book`
  (`name`, `author`, `isbn`, `publisher`, `category`, `total_count`, `available_count`, `cover`, `description`, `create_time`, `is_deleted`, `bookshelf_id`)
SELECT
  '星阑采书札', '栖星社编', '9900000000004', '星阑书社', '科学', 5, 2, NULL,
  '用清单和短札解释馆藏补充、版本选择与库存判断。',
  '2026-07-20 10:15:00', 0, shelf.id
FROM `bookshelf` shelf
WHERE shelf.name = '暗室总架'
  AND NOT EXISTS (SELECT 1 FROM `book` WHERE `name` = '星阑采书札')
LIMIT 1;

INSERT INTO `book`
  (`name`, `author`, `isbn`, `publisher`, `category`, `total_count`, `available_count`, `cover`, `description`, `create_time`, `is_deleted`, `bookshelf_id`)
SELECT
  '青梧守卷录', '青梧馆记', '9900000000005', '青梧文献馆', '哲学', 4, 2, NULL,
  '围绕保存、开放与秩序，讨论馆员如何守护公共阅读。',
  '2026-07-20 10:20:00', 0, shelf.id
FROM `bookshelf` shelf
WHERE shelf.name = '暗室总架'
  AND NOT EXISTS (SELECT 1 FROM `book` WHERE `name` = '青梧守卷录')
LIMIT 1;

INSERT INTO `book`
  (`name`, `author`, `isbn`, `publisher`, `category`, `total_count`, `available_count`, `cover`, `description`, `create_time`, `is_deleted`, `bookshelf_id`)
SELECT
  '砚灯拾页集', '砚灯读书会', '9900000000006', '砚灯小筑', '艺术', 3, 1, NULL,
  '收录读者在灯下留下的短评、页边批注与阅读路径。',
  '2026-07-20 10:25:00', 0, shelf.id
FROM `bookshelf` shelf
WHERE shelf.name = '暗室总架'
  AND NOT EXISTS (SELECT 1 FROM `book` WHERE `name` = '砚灯拾页集')
LIMIT 1;

INSERT INTO `notice` (`name`, `content`, `create_time`)
SELECT '雾灯厅开放时间', '<p>雾灯厅本周六延长开放至 21:30，请在闭馆前完成借阅登记。</p>', '2026-07-21 08:30:00'
WHERE NOT EXISTS (SELECT 1 FROM `notice` WHERE `name` = '雾灯厅开放时间');

INSERT INTO `notice` (`name`, `content`, `create_time`)
SELECT '六册新藏已编目', '<p>《暗室藏书》等六册演示藏书已经完成分类、上架与库存登记。</p>', '2026-07-22 09:00:00'
WHERE NOT EXISTS (SELECT 1 FROM `notice` WHERE `name` = '六册新藏已编目');

INSERT INTO `notice` (`name`, `content`, `create_time`)
SELECT '归还与预约提醒', '<p>收到到馆通知后，请在保留期内完成借阅；逾期预约将自动释放。</p>', '2026-07-23 09:20:00'
WHERE NOT EXISTS (SELECT 1 FROM `notice` WHERE `name` = '归还与预约提醒');

INSERT INTO `book_review` (`user_id`, `book_id`, `rating`, `content`, `status`, `create_time`)
SELECT reader_user.id, target_book.id, 5,
       '它把借阅写成一次有去有回的相遇，最喜欢其中关于等待归还的那一页。',
       0, '2026-07-24 20:18:00'
FROM `user` reader_user
JOIN `book` target_book ON target_book.name = '暗室藏书'
WHERE reader_user.user_account = 'drl_reader_yandeng'
  AND NOT EXISTS (
    SELECT 1
    FROM `book_review` review
    WHERE review.user_id = reader_user.id
      AND review.book_id = target_book.id
      AND review.content = '它把借阅写成一次有去有回的相遇，最喜欢其中关于等待归还的那一页。'
  )
LIMIT 1;

INSERT INTO `book_review` (`user_id`, `book_id`, `rating`, `content`, `status`, `create_time`)
SELECT reader_user.id, target_book.id, 4,
       '目录看似安静，实际把一座城的阅读痕迹串得很清楚，适合慢慢翻。',
       0, '2026-07-23 18:42:00'
FROM `user` reader_user
JOIN `book` target_book ON target_book.name = '雾灯索引'
WHERE reader_user.user_account = 'drl_reader_zhiyue'
  AND NOT EXISTS (
    SELECT 1
    FROM `book_review` review
    WHERE review.user_id = reader_user.id
      AND review.book_id = target_book.id
      AND review.content = '目录看似安静，实际把一座城的阅读痕迹串得很清楚，适合慢慢翻。'
  )
LIMIT 1;

INSERT INTO `book_review_reply`
  (`review_id`, `user_id`, `reply_to_user_id`, `content`, `create_time`)
SELECT review.id, keeper.id, reader_user.id,
       '这段批注已经收入本周馆员荐读，感谢你把归还之后的感受也留下来。',
       '2026-07-24 21:05:00'
FROM `book_review` review
JOIN `user` reader_user ON reader_user.id = review.user_id
JOIN `user` keeper ON keeper.user_account = 'drl_keeper_qingwu'
JOIN `book` target_book ON target_book.id = review.book_id
WHERE reader_user.user_account = 'drl_reader_yandeng'
  AND target_book.name = '暗室藏书'
  AND NOT EXISTS (
    SELECT 1
    FROM `book_review_reply` reply
    WHERE reply.review_id = review.id
      AND reply.user_id = keeper.id
      AND reply.content = '这段批注已经收入本周馆员荐读，感谢你把归还之后的感受也留下来。'
  )
LIMIT 1;

INSERT IGNORE INTO `book_review_like` (`review_id`, `user_id`, `create_time`)
SELECT review.id, liker.id, '2026-07-24 21:12:00'
FROM `book_review` review
JOIN `book` target_book ON target_book.id = review.book_id
JOIN `user` reviewer ON reviewer.id = review.user_id
JOIN `user` liker ON liker.user_account = 'drl_reader_zhiyue'
WHERE reviewer.user_account = 'drl_reader_yandeng'
  AND target_book.name = '暗室藏书'
LIMIT 1;

INSERT INTO `book_review_report`
  (`review_id`, `user_id`, `reason`, `status`, `create_time`, `handle_time`)
SELECT review.id, reporter.id, '包含关键情节，希望增加剧透提示。', 0,
       '2026-07-24 21:20:00', NULL
FROM `book_review` review
JOIN `book` target_book ON target_book.id = review.book_id
JOIN `user` reviewer ON reviewer.id = review.user_id
JOIN `user` reporter ON reporter.user_account = 'drl_reader_zhiyue'
WHERE reviewer.user_account = 'drl_reader_yandeng'
  AND target_book.name = '暗室藏书'
  AND NOT EXISTS (
    SELECT 1
    FROM `book_review_report` report
    WHERE report.review_id = review.id
      AND report.user_id = reporter.id
  )
LIMIT 1;

INSERT IGNORE INTO `book_favorite` (`user_id`, `book_id`, `create_time`)
SELECT reader_user.id, target_book.id, '2026-07-23 19:00:00'
FROM `user` reader_user
JOIN `book` target_book ON target_book.name = '青梧守卷录'
WHERE reader_user.user_account = 'drl_reader_yandeng'
LIMIT 1;

INSERT INTO `borrow_record`
  (`user_id`, `book_id`, `borrow_time`, `due_date`, `return_time`, `status`, `fine_amount`, `renew_count`)
SELECT reader_user.id, target_book.id, '2026-06-20 10:00:00',
       '2026-07-20 10:00:00', '2026-07-18 16:30:00', 1, 0.00, 0
FROM `user` reader_user
JOIN `book` target_book ON target_book.name = '青梧守卷录'
WHERE reader_user.user_account = 'drl_reader_yandeng'
  AND NOT EXISTS (
    SELECT 1
    FROM `borrow_record` record
    WHERE record.user_id = reader_user.id
      AND record.book_id = target_book.id
      AND record.borrow_time = '2026-06-20 10:00:00'
  )
LIMIT 1;

INSERT INTO `message_board`
  (`user_id`, `content`, `attachment_url`, `attachment_name`, `attachment_type`, `create_time`, `reply`)
SELECT reader_user.id,
       '雾灯厅靠窗的位置阅读灯有些暗，能否在下次巡检时确认一下？',
       NULL, NULL, NULL, '2026-07-24 17:15:00',
       '已登记到馆务巡检单，今晚闭馆后检查灯具与插座。'
FROM `user` reader_user
WHERE reader_user.user_account = 'drl_reader_yandeng'
  AND NOT EXISTS (
    SELECT 1
    FROM `message_board`
    WHERE `content` = '雾灯厅靠窗的位置阅读灯有些暗，能否在下次巡检时确认一下？'
  )
LIMIT 1;

INSERT INTO `procurement_order`
  (`book_id`, `book_name`, `isbn`, `category`, `request_count`, `status`,
   `requester_id`, `purchaser_id`, `logistics_id`, `request_note`, `purchase_note`,
   `stock_applied`, `create_time`, `update_time`, `order_time`, `shipped_time`)
SELECT target_book.id, target_book.name, target_book.isbn, target_book.category,
       7, 3, root_user.id, buyer.id, logistics.id,
       '当前可借库存为零，请补充馆藏并保留同版次。',
       '已核对供货清单，共七册，分两箱发出。',
       0, '2026-07-22 10:00:00', '2026-07-25 09:18:00',
       '2026-07-23 11:20:00', '2026-07-25 08:40:00'
FROM `book` target_book
JOIN `user` root_user ON root_user.user_account = 'drl_root_aurora'
JOIN `user` buyer ON buyer.user_account = 'drl_buyer_xinglan'
JOIN `user` logistics ON logistics.user_account = 'drl_logistics_chenxiang'
WHERE target_book.name = '归架之前'
  AND NOT EXISTS (
    SELECT 1
    FROM `procurement_order`
    WHERE `book_id` = target_book.id
      AND `create_time` = '2026-07-22 10:00:00'
  )
LIMIT 1;

INSERT INTO `procurement_logistics`
  (`order_id`, `logistics_user_id`, `status`, `tracking_no`, `carrier`, `remark`, `create_time`, `update_time`)
SELECT procurement.id, logistics.id, 1, 'DRL-20260725-0701',
       '雾桥馆配', '两箱均已装车，预计当日傍晚到馆。',
       '2026-07-25 08:45:00', '2026-07-25 09:18:00'
FROM `procurement_order` procurement
JOIN `book` target_book ON target_book.id = procurement.book_id
JOIN `user` logistics ON logistics.user_account = 'drl_logistics_chenxiang'
WHERE target_book.name = '归架之前'
  AND procurement.create_time = '2026-07-22 10:00:00'
  AND NOT EXISTS (
    SELECT 1 FROM `procurement_logistics` WHERE `order_id` = procurement.id
  )
LIMIT 1;

INSERT INTO `procurement_message`
  (`order_id`, `channel_type`, `sender_id`, `receiver_id`, `content`, `read_status`, `read_time`, `create_time`)
SELECT procurement.id, 1, buyer.id, logistics.id,
       '两箱图书已交接，请按运单登记到馆时间。', 0, NULL,
       '2026-07-25 09:30:00'
FROM `procurement_order` procurement
JOIN `book` target_book ON target_book.id = procurement.book_id
JOIN `user` buyer ON buyer.user_account = 'drl_buyer_xinglan'
JOIN `user` logistics ON logistics.user_account = 'drl_logistics_chenxiang'
WHERE target_book.name = '归架之前'
  AND procurement.create_time = '2026-07-22 10:00:00'
  AND NOT EXISTS (
    SELECT 1
    FROM `procurement_message`
    WHERE `order_id` = procurement.id
      AND `content` = '两箱图书已交接，请按运单登记到馆时间。'
  )
LIMIT 1;

INSERT INTO `operation_log`
  (`user_id`, `user_name`, `operation`, `target`, `detail`, `ip`, `create_time`)
SELECT keeper.id, keeper.user_name, 'DEMO_CONTENT_REVIEW',
       '书评：暗室藏书', '演示数据：登记书评回复并进入内容审核队列。',
       '127.0.0.1', '2026-07-24 21:25:00'
FROM `user` keeper
WHERE keeper.user_account = 'drl_keeper_qingwu'
  AND NOT EXISTS (
    SELECT 1
    FROM `operation_log`
    WHERE `operation` = 'DEMO_CONTENT_REVIEW'
      AND `create_time` = '2026-07-24 21:25:00'
  )
LIMIT 1;
