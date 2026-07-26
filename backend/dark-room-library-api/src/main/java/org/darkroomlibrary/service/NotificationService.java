package org.darkroomlibrary.service;

public interface NotificationService {

    void enqueueEmail(String receiverEmail, String subject, String content);

    void processTask(Integer taskId);

    void retryPendingTasks();
}
