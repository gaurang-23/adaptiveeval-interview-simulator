package com.interviewsimulator.backend.common;

public enum DifficultyLevel {
    EASY(1, 0.85),
    MEDIUM(2, 1.0),
    HARD(3, 1.2);

    private final int rank;
    private final double difficultyWeight;

    DifficultyLevel(int rank, double difficultyWeight) {
        this.rank = rank;
        this.difficultyWeight = difficultyWeight;
    }

    public int getRank() {
        return rank;
    }

    public double getDifficultyWeight() {
        return difficultyWeight;
    }

    public DifficultyLevel stepDown() {
        return switch (this) {
            case HARD -> MEDIUM;
            case MEDIUM, EASY -> EASY;
        };
    }

    public DifficultyLevel stepUp() {
        return switch (this) {
            case EASY -> MEDIUM;
            case MEDIUM, HARD -> HARD;
        };
    }

    public static DifficultyLevel fromString(String value) {
        if (value == null) return MEDIUM;
        try {
            return DifficultyLevel.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return MEDIUM;
        }
    }
}
