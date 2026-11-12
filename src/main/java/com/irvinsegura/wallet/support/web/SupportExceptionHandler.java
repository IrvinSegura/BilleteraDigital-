package com.irvinsegura.wallet.support.web;

import com.irvinsegura.wallet.shared.domain.DomainException;
import com.irvinsegura.wallet.shared.web.Problems;
import com.irvinsegura.wallet.support.domain.model.AgentNotAvailableException;
import com.irvinsegura.wallet.support.domain.model.AttachmentNotFoundException;
import com.irvinsegura.wallet.support.domain.model.BotSuggestionNotFoundException;
import com.irvinsegura.wallet.support.domain.model.BotSuggestionReviewedException;
import com.irvinsegura.wallet.support.domain.model.ConversationNotFoundException;
import com.irvinsegura.wallet.support.domain.model.OperationNotFoundException;
import com.irvinsegura.wallet.support.domain.model.SavedReplyLimitException;
import com.irvinsegura.wallet.support.domain.model.SavedReplyNotFoundException;
import com.irvinsegura.wallet.support.domain.model.TicketAlreadyOpenException;
import com.irvinsegura.wallet.support.domain.model.TicketNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Support errors that are not plain business rule violations (422).
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class SupportExceptionHandler {

    // Other people's conversations get the same answer, so ids cannot be probed
    @ExceptionHandler({ConversationNotFoundException.class, AttachmentNotFoundException.class,
            TicketNotFoundException.class, OperationNotFoundException.class, SavedReplyNotFoundException.class,
            BotSuggestionNotFoundException.class})
    ProblemDetail handleNotFound(DomainException ex) {
        return Problems.create(HttpStatus.NOT_FOUND, "Not found", ex.getMessage(), ex.getCode());
    }

    @ExceptionHandler({TicketAlreadyOpenException.class, AgentNotAvailableException.class,
            SavedReplyLimitException.class, BotSuggestionReviewedException.class})
    ProblemDetail handleAlreadyOpen(DomainException ex) {
        return Problems.create(HttpStatus.CONFLICT, "Conflict", ex.getMessage(), ex.getCode());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail handleTooLarge(MaxUploadSizeExceededException ex) {
        return Problems.create(HttpStatus.CONTENT_TOO_LARGE, "File too large", "Files can be up to 20 MB",
                "SUPPORT_ATTACHMENT_NOT_ALLOWED");
    }
}
