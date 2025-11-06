package com.example.recipealarm;

import java.util.List;

public class Recipe {
    private final String name;
    private final List<RecipeStep> steps;

    public Recipe(String name, List<RecipeStep> steps) {
        this.name = name;
        this.steps = steps;
    }

    public String getName() {
        return name;
    }

    public List<RecipeStep> getSteps() {
        return steps;
    }
}
