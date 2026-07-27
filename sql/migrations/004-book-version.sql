ALTER TABLE `book`
    ADD COLUMN `version` int NOT NULL DEFAULT 0
        COMMENT 'optimistic concurrency version' AFTER `id`;
