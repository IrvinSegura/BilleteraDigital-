package com.irvinsegura.wallet.identity.domain.model;

public enum UserStatus {
    ACTIVE,
    // Set by an administrator, e.g. for a compromised account: no login and no money movement until enabled again
    DISABLED,
    // Closed by its owner, with nothing left in it: no login, ever, and an administrator cannot open it again
    CLOSED
}
