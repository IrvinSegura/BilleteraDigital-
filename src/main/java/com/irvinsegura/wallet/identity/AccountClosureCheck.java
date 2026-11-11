package com.irvinsegura.wallet.identity;

import java.util.Optional;
import java.util.UUID;

/**
 * Something another module needs settled before an account can be closed, such as money left in a wallet. Each
 * module that holds something of the user's provides one as a bean; closing asks them all, inside the transaction
 * that closes the account, while the user's row is locked so no money movement can start in between.
 */
public interface AccountClosureCheck {

    /**
     * @return why the account cannot be closed yet, as a short code the app explains (e.g. {@code BALANCE}); empty
     *         if nothing here stops it
     */
    Optional<String> blocker(UUID userId);
}
