package org.darkroomlibrary.service;

/**
 * Records semantic audit events for sensitive operational actions.
 */
public interface OperationAuditService {

    void record(String operation, String target, String detail);
}
