package com.irvinsegura.wallet.notifications.web;

import com.irvinsegura.wallet.notifications.domain.model.Notification;
import com.irvinsegura.wallet.notifications.domain.port.LiveNotifier;
import java.io.IOException;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The open event streams of this instance, by user. With several instances each one only reaches the apps
 * connected to it; the others still see the notification on their next poll.
 */
@Component
class NotificationStreamHub implements LiveNotifier {

    // Shorter than an access token's life, so a stream never outlives the token that opened it by much; the app
    // reconnects with a fresh one
    static final Duration STREAM_TIMEOUT = Duration.ofMinutes(5);
    // Tabs and devices of one user; the oldest stream is closed past this
    private static final int MAX_STREAMS_PER_USER = 5;

    private final Map<UUID, Set<SseEmitter>> streams = new ConcurrentHashMap<>();

    SseEmitter open(UUID userId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT.toMillis());
        Set<SseEmitter> userStreams = streams.computeIfAbsent(userId, id -> ConcurrentHashMap.newKeySet());
        if (userStreams.size() >= MAX_STREAMS_PER_USER) {
            Iterator<SseEmitter> oldest = userStreams.iterator();
            if (oldest.hasNext()) {
                oldest.next().complete();
            }
        }
        userStreams.add(emitter);
        Runnable remove = () -> remove(userId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());
        // Tells the app the stream is live, so it can stop polling
        send(userId, emitter, SseEmitter.event().name("ready").data("{}", MediaType.APPLICATION_JSON));
        return emitter;
    }

    @Override
    public void push(Notification notification) {
        Set<SseEmitter> userStreams = streams.get(notification.userId());
        if (userStreams == null) {
            return;
        }
        LiveNotification body = new LiveNotification(notification.id(), notification.type().name(),
                notification.title());
        for (SseEmitter emitter : userStreams) {
            send(notification.userId(), emitter, SseEmitter.event().name("notification")
                    .id(notification.id().toString()).data(body, MediaType.APPLICATION_JSON));
        }
    }

    /**
     * A comment every so often keeps proxies from closing a stream that is quiet for a while.
     */
    @Scheduled(fixedDelay = 25_000)
    void heartbeat() {
        streams.forEach((userId, userStreams) -> userStreams.forEach(
                emitter -> send(userId, emitter, SseEmitter.event().comment("ping"))));
    }

    int openStreams(UUID userId) {
        Set<SseEmitter> userStreams = streams.get(userId);
        return userStreams == null ? 0 : userStreams.size();
    }

    private void send(UUID userId, SseEmitter emitter, SseEmitter.SseEventBuilder event) {
        try {
            emitter.send(event);
        } catch (IOException | IllegalStateException e) {
            // The app went away: drop the stream, it reconnects if it comes back. Not completeWithError: the
            // container already ended the request and reports it through onError, and touching it again from
            // this thread throws, which cut short the heartbeat for every stream after this one
            remove(userId, emitter);
        }
    }

    private void remove(UUID userId, SseEmitter emitter) {
        streams.computeIfPresent(userId, (id, userStreams) -> {
            userStreams.remove(emitter);
            return userStreams.isEmpty() ? null : userStreams;
        });
    }

    /**
     * What the app needs to refresh and show a toast; the full notification comes from the inbox.
     */
    record LiveNotification(UUID id, String type, String title) {
    }
}
