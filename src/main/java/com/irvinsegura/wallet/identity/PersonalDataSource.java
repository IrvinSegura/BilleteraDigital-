package com.irvinsegura.wallet.identity;

import java.util.UUID;

/**
 * One part of the copy of their data that users can download. Each module that keeps something about the user
 * provides one as a bean; the export puts each under its {@link #section()}.
 */
public interface PersonalDataSource {

    /**
     * The key the data goes under, e.g. {@code wallets}.
     */
    String section();

    /**
     * Plain records, lists and maps, written as JSON. Never secrets: no password hashes, tokens or full CLABEs.
     */
    Object collect(UUID userId);
}
