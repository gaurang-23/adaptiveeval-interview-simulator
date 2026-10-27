package com.interviewsimulator.backend.common;

public enum CompetencyType {
    PATTERN_RECOGNITION("Pattern Recognition"),
    IMPLEMENTATION("Implementation"),
    COMPLEXITY_ANALYSIS("Complexity Analysis"),
    DEBUGGING("Debugging"),
    COMMUNICATION("Communication");

    private final String label;

    CompetencyType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
