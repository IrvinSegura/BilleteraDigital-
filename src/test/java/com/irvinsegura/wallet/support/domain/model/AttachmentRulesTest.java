package com.irvinsegura.wallet.support.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AttachmentRulesTest {

    private static final UUID CONVERSATION = UUID.randomUUID();
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0};

    @Test
    void readsTheTypeFromTheBytesWhateverTheNameSays() {
        assertThat(AttachmentRules.describe(CONVERSATION, "notes.txt", PNG).contentType()).isEqualTo("image/png");
    }

    @ParameterizedTest
    @CsvSource({"%PDF-1.7 rest,application/pdf", "RIFF0000WEBPVP8,image/webp", "0000ftypisom,video/mp4"})
    void acceptsWhatCanBeSent(String start, String contentType) {
        assertThat(AttachmentRules.typeOf(start.getBytes(StandardCharsets.US_ASCII))).contains(contentType);
    }

    @Test
    void refusesWhatCouldRunInAReadersBrowser() {
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8);
        byte[] html = "<!doctype html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> AttachmentRules.describe(CONVERSATION, "a.png", svg))
                .isInstanceOf(AttachmentNotAllowedException.class);
        assertThatThrownBy(() -> AttachmentRules.describe(CONVERSATION, "a.pdf", html))
                .isInstanceOf(AttachmentNotAllowedException.class);
    }

    @Test
    void refusesEmptyAndOversizedFiles() {
        assertThatThrownBy(() -> AttachmentRules.describe(CONVERSATION, "a.png", new byte[0]))
                .isInstanceOf(AttachmentNotAllowedException.class);
        byte[] tooBig = new byte[(int) AttachmentRules.MAX_BYTES + 1];
        System.arraycopy(PNG, 0, tooBig, 0, PNG.length);
        assertThatThrownBy(() -> AttachmentRules.describe(CONVERSATION, "a.png", tooBig))
                .isInstanceOf(AttachmentNotAllowedException.class);
    }

    @Test
    void keepsOnlyASafeName() {
        assertThat(AttachmentRules.safeName("../../etc/passwd")).isEqualTo("passwd");
        assertThat(AttachmentRules.safeName("C:\\fotos\\mi foto.png")).isEqualTo("mi foto.png");
        assertThat(AttachmentRules.safeName("a\"b\r\nc.png")).isEqualTo("abc.png");
        assertThat(AttachmentRules.safeName(null)).isEqualTo("file");
        assertThat(AttachmentRules.safeName("..")).isEqualTo("file");
        assertThat(AttachmentRules.safeName("x".repeat(300) + ".png")).hasSize(150).endsWith(".png");
    }
}
