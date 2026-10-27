package com.interviewsimulator.backend.resume;

import com.interviewsimulator.backend.ai.AiConnectionStatus;

public class ResumeAnalysisProviderException extends RuntimeException {

    private final AiConnectionStatus connectionStatus;
    private final String errorCategory;
    private final String troubleshootingRecommendation;
    private final Long failedAnalysisId;

    public ResumeAnalysisProviderException(
            String message,
            AiConnectionStatus connectionStatus,
            String errorCategory,
            String troubleshootingRecommendation,
            Long failedAnalysisId,
            Throwable cause
    ) {
        super(message, cause);
        this.connectionStatus = connectionStatus;
        this.errorCategory = errorCategory;
        this.troubleshootingRecommendation = troubleshootingRecommendation;
        this.failedAnalysisId = failedAnalysisId;
    }

    public AiConnectionStatus getConnectionStatus() {
        return connectionStatus;
    }

    public String getErrorCategory() {
        return errorCategory;
    }

    public String getTroubleshootingRecommendation() {
        return troubleshootingRecommendation;
    }

    public Long getFailedAnalysisId() {
        return failedAnalysisId;
    }
}
