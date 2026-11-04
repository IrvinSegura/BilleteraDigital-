package com.irvinsegura.wallet.identity.domain.port;

import com.irvinsegura.wallet.identity.domain.model.ClientInfo;

/**
 * The client of the request being served; {@link ClientInfo#unknown()} outside a request.
 */
public interface ClientInfoSource {

    ClientInfo current();
}
