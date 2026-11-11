package com.irvinsegura.wallet.support.domain.model;

import com.irvinsegura.wallet.shared.domain.DomainException;

public class SurveyNotAvailableException extends DomainException {

    public SurveyNotAvailableException() {
        super("SUPPORT_SURVEY_NOT_AVAILABLE", "There is no support conversation waiting to be rated");
    }
}
