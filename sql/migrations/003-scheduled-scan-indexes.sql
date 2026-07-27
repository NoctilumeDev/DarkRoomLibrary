ALTER TABLE `borrow_record`
    ADD KEY `idx_due_reminder_scan` (`status`, `due_reminder_sent_time`, `due_date`);

ALTER TABLE `book_reservation`
    ADD KEY `idx_status_notify_book` (`status`, `notify_time`, `book_id`);

ALTER TABLE `stored_file`
    ADD KEY `idx_status_update` (`status`, `update_time`);
