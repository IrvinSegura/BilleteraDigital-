package com.irvinsegura.wallet.shared.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Answers the errors that never reach a controller (a path the security firewall rejects, such as one with
 * {@code //}) in the same Problem Details shape as the rest of the API, instead of Spring Boot's default body, which
 * echoes the path back.
 */
@RestController
class ProblemErrorController implements ErrorController {

    @RequestMapping("/error")
    ResponseEntity<ProblemDetail> error(HttpServletRequest request) {
        HttpStatus status = statusOf(request);
        ProblemDetail problem = Problems.create(status, status.getReasonPhrase(),
                status.is5xxServerError() ? "An unexpected error occurred" : "The request could not be processed",
                status.is5xxServerError() ? "INTERNAL_ERROR" : status.name());
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    private static HttpStatus statusOf(HttpServletRequest request) {
        HttpStatus status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer code
                ? HttpStatus.resolve(code) : null;
        return status != null ? status : HttpStatus.INTERNAL_SERVER_ERROR;
    }
}
