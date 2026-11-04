package com.irvinsegura.wallet.accounts.web;

import com.irvinsegura.wallet.accounts.application.BalanceHistory;
import com.irvinsegura.wallet.accounts.application.MonthSummary;
import com.irvinsegura.wallet.accounts.application.MovementCriteria;
import com.irvinsegura.wallet.accounts.application.WalletLimits;
import com.irvinsegura.wallet.accounts.application.WalletService;
import com.irvinsegura.wallet.accounts.application.WalletSummary;
import com.irvinsegura.wallet.ledger.AccountMovement;
import com.irvinsegura.wallet.ledger.DailyBalance;
import com.irvinsegura.wallet.ledger.MovementPage;
import com.irvinsegura.wallet.shared.web.MoneyResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Wallets of the authenticated user. The owner always comes from the token, so users only ever see their own.
 */
@RestController
@RequestMapping("/api/v1/wallets")
@SecurityRequirement(name = "bearerAuth")
class WalletController {

    private static final int MAX_STATEMENT_DAYS = 366;

    private final WalletService walletService;

    WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @PostMapping
    ResponseEntity<WalletResponse> open(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody OpenWalletRequest request) {
        WalletSummary wallet = walletService.openWallet(userId(jwt), request.currency());
        return ResponseEntity.created(URI.create("/api/v1/wallets/" + wallet.id()))
                .body(WalletResponse.from(wallet));
    }

    @GetMapping
    List<WalletResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return walletService.listWallets(userId(jwt)).stream().map(WalletResponse::from).toList();
    }

    @GetMapping("/{walletId}")
    WalletResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID walletId) {
        return WalletResponse.from(walletService.getWallet(walletId, userId(jwt)));
    }

    /**
     * Statement page, newest first. {@code from} and {@code to} are the users' calendar days, both included;
     * {@code type} may repeat; {@code q} matches the start of the operation id. Every filter is optional.
     */
    @GetMapping("/{walletId}/movements")
    MovementPageResponse movements(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID walletId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "type", required = false)
            Set<@Pattern(regexp = "[A-Z_]{1,40}", message = "must be an entry type") String> types,
            @RequestParam(required = false) AccountMovement.Direction direction,
            @RequestParam(required = false)
            @Pattern(regexp = "[0-9a-fA-F-]{1,36}", message = "must be the start of an operation id") String q) {
        requireOrdered(from, to);
        MovementCriteria criteria = new MovementCriteria(from, to, types, direction, q);
        return MovementPageResponse.from(walletService.movements(walletId, userId(jwt), criteria, page, size));
    }

    /**
     * One movement, by the {@code transactionId} the statement lists, for its receipt.
     */
    @GetMapping("/{walletId}/movements/{transactionId}")
    MovementResponse movement(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID walletId,
            @PathVariable UUID transactionId) {
        return MovementResponse.from(walletService.movement(walletId, userId(jwt), transactionId));
    }

    /**
     * Every movement from {@code from} to {@code to} (at most 366 days, both included), oldest first, as a CSV
     * file for spreadsheets. Times are in the zone the users' days follow.
     */
    @GetMapping(value = "/{walletId}/statement", produces = "text/csv")
    ResponseEntity<String> statement(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID walletId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        requireOrdered(from, to);
        if (ChronoUnit.DAYS.between(from, to) >= MAX_STATEMENT_DAYS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A statement covers at most " + MAX_STATEMENT_DAYS + " days");
        }
        List<AccountMovement> movements = walletService.statement(walletId, userId(jwt), from, to);
        ZoneId zone = walletService.usersZone();
        StringBuilder csv = new StringBuilder("posted_at,type,direction,amount,currency,balance_after,reference\r\n");
        // Every value is a code, a number, a date or an id, so none needs quoting and none can start a formula
        for (AccountMovement movement : movements) {
            csv.append(movement.postedAt().atZone(zone).toOffsetDateTime().truncatedTo(ChronoUnit.SECONDS))
                    .append(',').append(movement.type())
                    .append(',').append(movement.direction())
                    .append(',').append(movement.amount().toDecimal().toPlainString())
                    .append(',').append(movement.amount().currency().getCurrencyCode())
                    .append(',').append(movement.balanceAfter().toDecimal().toPlainString())
                    .append(',').append(movement.reference() == null ? "" : movement.reference())
                    .append("\r\n");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("statement-" + from + "-" + to + ".csv").build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString());
    }

    /**
     * Money in and out of the wallet in a calendar month, the current one by default, summed over every movement
     * (a statement page would only cover its newest ones).
     */
    @GetMapping("/{walletId}/summary")
    MonthSummaryResponse summary(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID walletId,
            @RequestParam(required = false)
            @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "must be a month as yyyy-MM") String month) {
        YearMonth period = month == null ? walletService.currentMonth() : YearMonth.parse(month);
        MonthSummary summary = walletService.monthSummary(walletId, userId(jwt), period);
        return new MonthSummaryResponse(period.toString(), MoneyResponse.from(summary.openingBalance()),
                MoneyResponse.from(summary.moneyIn()), MoneyResponse.from(summary.moneyOut()),
                MoneyResponse.from(summary.closingBalance()));
    }

    /**
     * The wallet's balance at the end of each of the last {@code days} days, today included, for charts.
     */
    @GetMapping("/{walletId}/balance-history")
    BalanceHistoryResponse balanceHistory(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID walletId,
            @RequestParam(defaultValue = "30") @Min(1) @Max(366) int days) {
        BalanceHistory history = walletService.balanceHistory(walletId, userId(jwt), days);
        return new BalanceHistoryResponse(MoneyResponse.from(history.openingBalance()),
                history.days().stream().map(BalancePointResponse::from).toList());
    }

    /**
     * The daily limit on money leaving the wallet and what is left of it today.
     */
    @GetMapping("/{walletId}/limits")
    WalletLimitsResponse limits(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID walletId) {
        WalletLimits limits = walletService.limits(walletId, userId(jwt));
        return new WalletLimitsResponse(limits.dailyLimit() == null ? null : MoneyResponse.from(limits.dailyLimit()),
                MoneyResponse.from(limits.usedToday()),
                limits.remainingToday() == null ? null : MoneyResponse.from(limits.remainingToday()),
                limits.resetsAt());
    }

    private static void requireOrdered(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from must not be after to");
        }
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    record OpenWalletRequest(@NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code")
            String currency) {
    }

    record WalletResponse(UUID id, MoneyResponse balance, String status, Instant createdAt) {

        static WalletResponse from(WalletSummary wallet) {
            return new WalletResponse(wallet.id(), MoneyResponse.from(wallet.balance()), wallet.status(),
                    wallet.createdAt());
        }
    }

    /**
     * {@code operationId} is the id of the deposit or transfer that caused the movement.
     */
    record MovementResponse(UUID transactionId, String type, String operationId, String direction,
            MoneyResponse amount, MoneyResponse balanceAfter, Instant createdAt) {

        static MovementResponse from(AccountMovement movement) {
            return new MovementResponse(movement.journalEntryId(), movement.type(), movement.reference(),
                    movement.direction().name(), MoneyResponse.from(movement.amount()),
                    MoneyResponse.from(movement.balanceAfter()), movement.postedAt());
        }
    }

    /**
     * {@code month} is {@code yyyy-MM}, in the zone that sets when the users' days start. {@code closingBalance} is
     * the balance so far while the month is not over.
     */
    record MonthSummaryResponse(String month, MoneyResponse openingBalance, MoneyResponse moneyIn,
            MoneyResponse moneyOut, MoneyResponse closingBalance) {
    }

    /**
     * @param dailyOutgoingLimit what can leave per day (withdrawals and transfers sent); {@code null} if uncapped
     * @param resetsAt           when the next day starts for the limit
     */
    record WalletLimitsResponse(MoneyResponse dailyOutgoingLimit, MoneyResponse usedToday,
            MoneyResponse remainingToday, Instant resetsAt) {
    }

    record BalancePointResponse(LocalDate date, MoneyResponse balance) {

        static BalancePointResponse from(DailyBalance point) {
            return new BalancePointResponse(point.date(), MoneyResponse.from(point.balance()));
        }
    }

    /**
     * {@code openingBalance} is the balance at the start of the first point's day.
     */
    record BalanceHistoryResponse(MoneyResponse openingBalance, List<BalancePointResponse> points) {
    }

    record MovementPageResponse(List<MovementResponse> items, int page, int size, boolean hasNext) {

        static MovementPageResponse from(MovementPage page) {
            return new MovementPageResponse(page.items().stream().map(MovementResponse::from).toList(),
                    page.page(), page.size(), page.hasNext());
        }
    }
}
