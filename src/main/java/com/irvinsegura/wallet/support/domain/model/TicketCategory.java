package com.irvinsegura.wallet.support.domain.model;

/**
 * What a ticket is about. Only {@link #OPERATION} tickets point at an operation; the rest are about the account itself,
 * such as a sign-in lock or an INE waiting too long for review.
 */
public enum TicketCategory {
    OPERATION,
    ACCOUNT_ACCESS,
    IDENTITY,
    OTHER
}
