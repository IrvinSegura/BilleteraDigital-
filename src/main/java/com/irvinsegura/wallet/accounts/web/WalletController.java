package com.irvinsegura.wallet.accounts.web;

import com.irvinsegura.wallet.accounts.application.BalanceHistory;
import com.irvinsegura.wallet.accounts.application.MovementCriteria;
import com.irvinsegura.wallet.accounts.application.PeriodSummary;
import com.irvinsegura.wallet.accounts.application.WalletLimits;
import com.irvinsegura.wallet.accounts.application.WalletMovement;
import com.irvinsegura.wallet.accounts.application.WalletMovementPage;
import com.irvinsegura.wallet.accounts.application.WalletService;
import com.irvinsegura.wallet.accounts.application.WalletSummary;
import com.irvinsegura.wallet.ledger.AccountMovement;
import com.irvinsegura.wallet.ledger.DailyBalance;
import com.irvinsegura.wallet.shared.web.MoneyResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
     * {@code type} may repeat; {@code q} matches the start of the operation id, or part of the other party's name or
     * of the description, ignoring case and accents. Every filter is optional.
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
            @RequestParam(required = false) @Size(max = 60)
            @Pattern(regexp = "\\P{Cntrl}*", message = "must not have control characters") String q) {
        requireOrdered(from, to);
        MovementCriteria criteria = new MovementCriteria(from, to, types, direction,
                q == null || q.isBlank() ? null : q.strip());
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
        List<WalletMovement> movements = walletService.statement(walletId, userId(jwt), from, to);
        ZoneId zone = walletService.usersZone();
        StringBuilder csv = new StringBuilder(
                "posted_at,type,direction,amount,currency,balance_after,reference,counterparty,description\r\n");
        for (WalletMovement item : movements) {
            AccountMovement movement = item.movement();
            csv.append(movement.postedAt().atZone(zone).toOffsetDateTime().truncatedTo(ChronoUnit.SECONDS))
                    .append(',').append(movement.type())
                    .append(',').append(movement.direction())
                    .append(',').append(movement.amount().toDecimal().toPlainString())
                    .append(',').append(movement.amount().currency().getCurrencyCode())
                    .append(',').append(movement.balanceAfter().toDecimal().toPlainString())
                    .append(',').append(movement.reference() == null ? "" : movement.reference())
                    .append(',').append(csvText(item.counterpartyName()))
                    .append(',').append(csvText(item.description()))
                    .append("\r\n");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("statement-" + from + "-" + to + ".csv").build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString());
    }

    /**
     * Money in and out of the wallet in a period, summed over every movement (a statement page would only cover its
     * newest ones): a calendar {@code month}, the current one by default, or the days from {@code from} to {@code to}
     * (both included, at most 366 days), which take precedence when both are given.
     */
    @GetMapping("/{walletId}/summary")
    MonthSummaryResponse summary(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID walletId,
            @RequestParam(required = false)
            @Pattern(regexp = "\\d{4}-(0[1-9]|1[0-2])", message = "must be a month as yyyy-MM") String month,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if ((from == null) != (to == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "from and to go together");
        }
        String monthLabel = null;
        if (from == null) {
            YearMonth period = month == null ? walletService.currentMonth() : YearMonth.parse(month);
            monthLabel = period.toString();
            from = period.atDay(1);
            to = period.atEndOfMonth();
        } else {
            requireOrdered(from, to);
            if (ChronoUnit.DAYS.between(from, to) >= MAX_STATEMENT_DAYS) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "A summary covers at most " + MAX_STATEMENT_DAYS + " days");
            }
        }
        PeriodSummary summary = walletService.periodSummary(walletId, userId(jwt), from, to);
        return new MonthSummaryResponse(monthLabel, summary.from(), summary.to(),
                MoneyResponse.from(summary.openingBalance()), MoneyResponse.from(summary.moneyIn()),
                MoneyResponse.from(summary.moneyOut()), MoneyResponse.from(summary.closingBalance()));
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

    // What people typed, unlike every other column: it may hold quotes or commas, and a cell starting with one of
    // these characters would run as a formula when the file is opened in a spreadsheet
    private static String csvText(String text) {
        if (text == null) {
            return "";
        }
        String safe = !text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0 ? "'" + text : text;
        return '"' + safe.replace("\"", "\"\"") + '"';
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
     * {@code operationId} is the id of the deposit or transfer that caused the movement. A transfer also has the
     * other party's {@code counterpartyName} and the {@code description} its sender wrote, if any.
     */
    record MovementResponse(UUID transactionId, String type, String operationId, String direction,
            MoneyResponse amount, MoneyResponse balanceAfter, Instant createdAt, String counterpartyName,
            String description) {

        static MovementResponse from(WalletMovement item) {
            AccountMovement movement = item.movement();
            return new MovementResponse(movement.journalEntryId(), movement.type(), movement.reference(),
                    movement.direction().name(), MoneyResponse.from(movement.amount()),
                    MoneyResponse.from(movement.balanceAfter()), movement.postedAt(), item.counterpartyName(),
                    item.description());
        }
    }

    /**
     * {@code month} is {@code yyyy-MM} when a month was asked for, and {@code null} for a period of days; {@code from}
     * and {@code to} are its first and last days either way, in the zone that sets when the users' days start.
     * {@code closingBalance} is the balance so far while the period is not over.
     */
    record MonthSummaryResponse(String month, LocalDate from, LocalDate to, MoneyResponse openingBalance,
            MoneyResponse moneyIn, MoneyResponse moneyOut, MoneyResponse closingBalance) {
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

        static MovementPageResponse from(WalletMovementPage page) {
            return new MovementPageResponse(page.items().stream().map(MovementResponse::from).toList(),
                    page.page(), page.size(), page.hasNext());
        }
    }
}
