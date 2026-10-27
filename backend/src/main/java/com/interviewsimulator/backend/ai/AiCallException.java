package com.interviewsimulator.backend.ai;

public class AiCallException extends RuntimeException {

    private final AiConnectionStatus connectionStatus;
    private final String errorCategory;
    private final Integer httpStatus;
    private final String troubleshootingRecommendation;

    public AiCallException(
            String message,
            AiConnectionStatus connectionStatus,
            String errorCategory,
            Integer httpStatus,
            String troubleshootingRecommendation,
            Throwable cause
    ) {
        super(message, cause);
        this.connectionStatus = connectionStatus;
        this.errorCategory = errorCategory;
        this.httpStatus = httpStatus;
        this.troubleshootingRecommendation = troubleshootingRecommendation;
    }

    public AiConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    public String getErrorCategory() {
        return errorCategory;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public String getTroubleshootingRecommendation() {
        return troubleshootingRecommendation;
    }
}
