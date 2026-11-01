package com.irvinsegura.wallet.identity.domain.model;

public enum UserStatus {
    ACTIVE,
    // Set by an administrator, e.g. for a compromised account: no login and no money movement until enabled again
    DISABLED
}
