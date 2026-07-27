ALTER TABLE `operation_log`
    ADD COLUMN `event_key` varchar(64) DEFAULT NULL COMMENT 'message idempotency key' AFTER `id`,
    ADD UNIQUE KEY `uk_operation_log_event_key` (`event_key`);
