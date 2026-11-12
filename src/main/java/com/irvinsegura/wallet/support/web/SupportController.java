package com.irvinsegura.wallet.support.web;

import com.irvinsegura.wallet.support.application.AttachmentDownload;
import com.irvinsegura.wallet.support.application.SupportChatService;
import com.irvinsegura.wallet.support.domain.model.SupportConversation;
import com.irvinsegura.wallet.support.web.ConversationResponses.ConversationResponse;
import com.irvinsegura.wallet.support.web.ConversationResponses.MessageRequest;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * The chat with support of whoever is signed in, who has one open conversation at a time, so no id is needed.
 */
@RestController
@RequestMapping("/api/v1/support")
@SecurityRequirement(name = "bearerAuth")
class SupportController {

    private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";
    private static final String CONVERSATION = "/conversation";

    private final SupportChatService chat;

    SupportController(SupportChatService chat) {
        this.chat = chat;
    }

    /**
     * The open conversation or the one waiting for its survey; {@code 204} when the user can just start writing.
     */
    @GetMapping(CONVERSATION)
    ResponseEntity<ConversationResponse> current(@AuthenticationPrincipal Jwt jwt) {
        return chat.current(userId(jwt)).map(ConversationResponse::from).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Sends a message, starting a conversation if there is none. While it is with the bot, the answer comes in the same
     * response, or it passes to a person if the bot has none.
     */
    @PostMapping(CONVERSATION + "/messages")
    ConversationResponse send(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MessageRequest request) {
        return ConversationResponse.from(chat.send(userId(jwt), request.text()));
    }

    /**
     * Sends a JPEG, PNG or WebP picture, a PDF or an MP4 video of up to 20 MB, with an optional text. The bot can't read
     * it, so the conversation passes to a person.
     */
    @PostMapping(path = CONVERSATION + "/attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ConversationResponse attach(@AuthenticationPrincipal Jwt jwt, @RequestParam MultipartFile file,
            @RequestParam(required = false) @Size(max = ConversationResponses.MAX_TEXT_LENGTH)
            @Pattern(regexp = ConversationResponses.MESSAGE_TEXT, message = ConversationResponses.MESSAGE_TEXT_ERROR)
            String text) throws IOException {
        return ConversationResponse.from(chat.attach(userId(jwt), file.getOriginalFilename(), file.getBytes(), text));
    }

    /**
     * Passes the conversation to a person, starting one if there is none.
     */
    @PostMapping(CONVERSATION + "/escalate")
    ConversationResponse escalate(@AuthenticationPrincipal Jwt jwt) {
        return ConversationResponse.from(chat.escalate(userId(jwt)));
    }

    /**
     * Closes the open conversation. If an agent attended it the response has it, waiting for its survey; otherwise
     * {@code 204}.
     */
    @PostMapping(CONVERSATION + "/close")
    ResponseEntity<ConversationResponse> close(@AuthenticationPrincipal Jwt jwt) {
        return chat.close(userId(jwt)).map(ConversationResponse::from).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /**
     * Rates the conversation waiting for its survey, from 1 to 5.
     */
    @PostMapping(CONVERSATION + "/survey")
    ResponseEntity<Void> survey(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SurveyRequest request) {
        chat.answerSurvey(userId(jwt), request.score(), request.comment());
        return ResponseEntity.noContent().build();
    }

    /**
     * Downloads a file of a conversation the user takes part in. Always as a download, and sandboxed if opened anyway,
     * because users choose what is in it.
     */
    @GetMapping("/attachments/{attachmentId}")
    ResponseEntity<byte[]> attachment(@AuthenticationPrincipal Jwt jwt, Authentication authentication,
            @PathVariable UUID attachmentId) {
        boolean administrator = authentication.getAuthorities().stream()
                .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
        AttachmentDownload download = chat.attachment(attachmentId, userId(jwt), administrator);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(download.attachment().contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.attachment().fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox; default-src 'none'")
                .body(download.content());
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    record SurveyRequest(@Min(SupportConversation.MIN_SCORE) @Max(SupportConversation.MAX_SCORE) int score,
            @Size(max = 500) @Pattern(regexp = ConversationResponses.MESSAGE_TEXT,
                    message = ConversationResponses.MESSAGE_TEXT_ERROR) String comment) {
    }
}
