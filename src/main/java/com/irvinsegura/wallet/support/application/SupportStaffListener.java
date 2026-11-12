package com.irvinsegura.wallet.support.application;

import com.irvinsegura.wallet.identity.UserRoleChanged;
import com.irvinsegura.wallet.identity.UserStatusChanged;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Keeps cases moving when the team changes, so nobody is left waiting for someone who can no longer answer.
 */
@Component
class SupportStaffListener {

    private static final String DISABLED = "DISABLED";
    private static final String SUPPORT = "SUPPORT";

    private final ConversationAssignments assignments;

    SupportStaffListener(ConversationAssignments assignments) {
        this.assignments = assignments;
    }

    @ApplicationModuleListener
    void on(UserRoleChanged event) {
        if (SUPPORT.equals(event.role())) {
            assignments.assignWaiting();
        } else {
            assignments.release(event.userId());
        }
    }

    @ApplicationModuleListener
    void on(UserStatusChanged event) {
        if (DISABLED.equals(event.status())) {
            assignments.release(event.userId());
        } else {
            assignments.assignWaiting();
        }
    }
}
