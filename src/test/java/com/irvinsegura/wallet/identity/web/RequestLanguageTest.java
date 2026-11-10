package com.irvinsegura.wallet.identity.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.irvinsegura.wallet.shared.domain.Language;
import org.junit.jupiter.api.Test;

class RequestLanguageTest {

    @Test
    void takesTheFirstLanguageEmailsExistIn() {
        assertThat(RequestLanguage.of("en")).isEqualTo(Language.EN);
        assertThat(RequestLanguage.of("es-MX,es;q=0.9,en;q=0.8")).isEqualTo(Language.ES);
        assertThat(RequestLanguage.of("fr-FR,en;q=0.7,es;q=0.5")).isEqualTo(Language.EN);
    }

    @Test
    void noUsableLanguageMeansNone() {
        assertThat(RequestLanguage.of(null)).isNull();
        assertThat(RequestLanguage.of(" ")).isNull();
        assertThat(RequestLanguage.of("fr")).isNull();
        assertThat(RequestLanguage.of("*")).isNull();
        assertThat(RequestLanguage.of("not a;;;header")).isNull();
    }
}
