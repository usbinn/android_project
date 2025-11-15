package com.example.recipealarm;

import java.util.List;
import java.util.UUID;

/**
 * 레시피 데이터를 나타내는 모델 클래스입니다.
 * 각 레시피는 고유 ID, 이름, 단계 목록, 즐겨찾기 상태를 가집니다.
 */
public class Recipe {
    private final String id;
    private final String name;
    private final List<RecipeStep> steps;
    private boolean isFavorite;

    /**
     * 새로운 레시피를 생성할 때 사용하는 생성자입니다.
     * 고유한 ID가 자동으로 생성됩니다.
     * @param name 레시피 이름
     * @param steps 레시피 단계 목록
     */
    public Recipe(String name, List<RecipeStep> steps) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.steps = steps;
        this.isFavorite = false;
    }

    /**
     * JSON 등에서 데이터를 불러와 객체를 생성할 때 사용하는 생성자입니다.
     * @param id 고유 ID
     * @param name 레시피 이름
     * @param steps 레시피 단계 목록
     * @param isFavorite 즐겨찾기 여부
     */
    public Recipe(String id, String name, List<RecipeStep> steps, boolean isFavorite) {
        this.id = id;
        this.name = name;
        this.steps = steps;
        this.isFavorite = isFavorite;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public List<RecipeStep> getSteps() {
        return steps;
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }
}
