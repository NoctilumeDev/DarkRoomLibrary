-- DarkRoomLibrary recoverable deletion upgrade.
-- Apply once to an existing database after taking a backup.
-- Run the session in the same wall-clock zone as APP_TIME_ZONE. The defaults below
-- match the application policy, while allowing an operator to override them before
-- sourcing this script in a controlled migration.

SET @recycle_retention_days = COALESCE(@recycle_retention_days, 30);
SET @recycle_migration_time = COALESCE(@recycle_migration_time, CURRENT_TIMESTAMP);

ALTER TABLE `book`
  ADD COLUMN `deleted_at` datetime DEFAULT NULL COMMENT 'entered recycle bin time' AFTER `is_deleted`,
  ADD COLUMN `restore_deadline` datetime DEFAULT NULL COMMENT 'restore eligibility deadline' AFTER `deleted_at`,
  ADD COLUMN `expired_at` datetime DEFAULT NULL COMMENT 'restore eligibility revoked time' AFTER `restore_deadline`,
  ADD KEY `idx_book_recycle_expiry` (`is_deleted`, `expired_at`, `restore_deadline`);

ALTER TABLE `book_review`
  ADD COLUMN `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT 'reader recycle-bin flag' AFTER `status`,
  ADD COLUMN `deleted_at` datetime DEFAULT NULL COMMENT 'entered recycle bin time' AFTER `is_deleted`,
  ADD COLUMN `restore_deadline` datetime DEFAULT NULL COMMENT 'restore eligibility deadline' AFTER `deleted_at`,
  ADD COLUMN `expired_at` datetime DEFAULT NULL COMMENT 'restore eligibility revoked time' AFTER `restore_deadline`,
  ADD KEY `idx_review_recycle_owner` (`user_id`, `is_deleted`, `expired_at`, `restore_deadline`);

ALTER TABLE `message_board`
  ADD COLUMN `is_deleted` tinyint NOT NULL DEFAULT 0 COMMENT 'reader recycle-bin flag' AFTER `reply`,
  ADD COLUMN `moderation_status` tinyint NOT NULL DEFAULT 0 COMMENT '0=public,1=removed by administrator' AFTER `is_deleted`,
  ADD COLUMN `deleted_at` datetime DEFAULT NULL COMMENT 'entered recycle bin time' AFTER `moderation_status`,
  ADD COLUMN `restore_deadline` datetime DEFAULT NULL COMMENT 'restore eligibility deadline' AFTER `deleted_at`,
  ADD COLUMN `expired_at` datetime DEFAULT NULL COMMENT 'restore eligibility revoked time' AFTER `restore_deadline`,
  ADD KEY `idx_message_visible` (`is_deleted`, `moderation_status`, `create_time`),
  ADD KEY `idx_message_recycle_owner` (`user_id`, `is_deleted`, `moderation_status`, `expired_at`, `restore_deadline`);

-- Existing deleted books predate the 30-day contract. Give them one explicit recovery window
-- beginning at migration time instead of silently treating them as permanently recoverable.
UPDATE `book`
SET `deleted_at` = @recycle_migration_time,
    `restore_deadline` = DATE_ADD(
      @recycle_migration_time,
      INTERVAL @recycle_retention_days DAY
    )
WHERE `is_deleted` = 1
  AND `restore_deadline` IS NULL;
