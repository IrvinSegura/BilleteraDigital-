package com.irvinsegura.wallet.transfers.infrastructure.persistence;

import com.irvinsegura.wallet.transfers.domain.port.WalletTransfers;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Plain JDBC: these read columns from both sides of a transfer, which the entity has no use for. They go through the
 * wallet indexes, so a search only ever reads the transfers of one wallet.
 */
@Repository
class WalletTransfersJdbcQuery implements WalletTransfers {

    // Stands in for an empty list, which SQL has no way to write; no user has this id
    private static final List<UUID> NOBODY = List.of(new UUID(0, 0));

    private final JdbcClient jdbcClient;

    WalletTransfersJdbcQuery(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<WalletTransfer> find(UUID walletId, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jdbcClient.sql("""
                        SELECT id, description,
                               CASE WHEN source_wallet_id = :walletId THEN recipient_id ELSE sender_id END AS counterparty
                        FROM transfers
                        WHERE id IN (:ids) AND (source_wallet_id = :walletId OR destination_wallet_id = :walletId)""")
                .param("walletId", walletId)
                .param("ids", ids)
                .query((rs, rowNum) -> new WalletTransfer(rs.getObject("id", UUID.class),
                        rs.getObject("counterparty", UUID.class), rs.getString("description")))
                .list();
    }

    @Override
    public Set<UUID> counterparties(UUID walletId) {
        return new HashSet<>(jdbcClient.sql("""
                        SELECT recipient_id AS counterparty FROM transfers WHERE source_wallet_id = :walletId
                        UNION
                        SELECT sender_id FROM transfers WHERE destination_wallet_id = :walletId""")
                .param("walletId", walletId)
                .query((rs, rowNum) -> rs.getObject("counterparty", UUID.class))
                .list());
    }

    @Override
    public List<UUID> findMatching(UUID walletId, String foldedText, Collection<UUID> counterparties, int limit) {
        // What was typed is matched literally: % and _ are not wildcards here
        String literal = foldedText.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return jdbcClient.sql("""
                        SELECT id FROM transfers
                        WHERE (source_wallet_id = :walletId OR destination_wallet_id = :walletId)
                          AND (translate(lower(description), 'áàäâãéèëêíìïîóòöôõúùüûñç', 'aaaaaeeeeiiiiooooouuuunc')
                                   LIKE :pattern ESCAPE '\\'
                               OR CASE WHEN source_wallet_id = :walletId THEN recipient_id ELSE sender_id END
                                   IN (:counterparties))
                        ORDER BY created_at DESC, id
                        LIMIT :limit""")
                .param("walletId", walletId)
                .param("pattern", "%" + literal + "%")
                .param("counterparties", counterparties.isEmpty() ? NOBODY : counterparties)
                .param("limit", limit)
                .query((rs, rowNum) -> rs.getObject("id", UUID.class))
                .list();
    }
}
