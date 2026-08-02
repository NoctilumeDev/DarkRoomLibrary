package org.darkroomlibrary.infrastructure.alert;

import org.darkroomlibrary.domain.model.NotificationTask;

public interface OperationalAlertService {

    void notificationTaskDead(NotificationTask task, int retryCount, String error);
}
