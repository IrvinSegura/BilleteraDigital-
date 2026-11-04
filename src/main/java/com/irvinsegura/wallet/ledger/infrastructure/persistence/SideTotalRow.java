package com.irvinsegura.wallet.ledger.infrastructure.persistence;

import com.irvinsegura.wallet.ledger.domain.model.Side;

/**
 * Query projection; public because Hibernate instantiates it from a JPQL constructor expression.
 */
public record SideTotalRow(Side side, Long totalMinor) {
}
