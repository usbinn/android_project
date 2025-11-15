package com.example.recipealarm;

/**
 * 레시피의 한 단계를 나타내는 데이터 클래스입니다.
 * 각 단계는 설명과 소요 시간(초)으로 구성됩니다.
 */
public class RecipeStep {
    private final String description;
    private final int durationInSeconds;

    /**
     * @param description 단계에 대한 설명 (예: "물 550ml 끓이기")
     * @param durationInSeconds 해당 단계에 소요되는 시간 (초 단위)
     */
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
