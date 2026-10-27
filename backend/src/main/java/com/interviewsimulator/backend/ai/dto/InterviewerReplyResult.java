package com.interviewsimulator.backend.ai.dto;

public record InterviewerReplyResult(
        String replyContent,
        String messageType,
        String stage,
        String sourceType,
        String fallbackReason
) {
    public InterviewerReplyResult(
            String replyContent,
            String messageType,
            String stage,
            String sourceType
    ) {
        this(replyContent, messageType, stage, sourceType, null);
    }
}
