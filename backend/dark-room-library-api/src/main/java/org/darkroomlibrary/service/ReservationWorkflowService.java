package org.darkroomlibrary.service;

public interface ReservationWorkflowService {

    void onBookReturned(Integer bookId);

    void expireOverdueNotifications();
}
