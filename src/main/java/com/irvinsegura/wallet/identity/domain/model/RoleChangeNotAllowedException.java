package com.irvinsegura.wallet.identity.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class RoleChangeNotAllowedException extends DomainException {

    public RoleChangeNotAllowedException() {
        super("ROLE_CHANGE_NOT_ALLOWED", "The administrator's role can't change, and nobody can be made an administrator");
    }
}
