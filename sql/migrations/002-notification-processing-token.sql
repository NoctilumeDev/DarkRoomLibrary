ALTER TABLE `notification_task`
    ADD COLUMN `processing_token` varchar(64) DEFAULT NULL
        COMMENT 'current processing lease owner' AFTER `last_error`;
