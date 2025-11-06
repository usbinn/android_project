package com.example.recipealarm;

public class RecipeStep {
    private final String description;
    private final int durationInSeconds;

    public RecipeStep(String description, int durationInSeconds) {
        this.description = description;
        this.durationInSeconds = durationInSeconds;
    }

    public String getDescription() {
        return description;
    }

    public int getDurationInSeconds() {
        return durationInSeconds;
    }
}
