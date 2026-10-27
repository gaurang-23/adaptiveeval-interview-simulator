package com.interviewsimulator.backend.ai;

public enum AiConnectionStatus {
    NOT_CONFIGURED,
    CONFIGURED_UNVERIFIED,
    CONNECTED,
    AUTH_ERROR,
    QUOTA_OR_BILLING_ERROR,
    MODEL_OR_ENDPOINT_ERROR,
    RATE_LIMITED,
    TIMEOUT,
    PROVIDER_ERROR
}
