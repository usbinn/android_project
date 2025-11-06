package com.example.recipealarm;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 레시피의 데이터 소스 역할을 하는 클래스입니다.
 *
 * 실제 애플리케이션에서는 이 클래스가 로컬 데이터베이스(예: Room)나 원격 서버에서 데이터를 가져올 수 있습니다.
 * 이 프로젝트에서는 하드코딩된 샘플 데이터를 제공합니다.
 */
public class RecipeRepository {

    /**
     * 사용 가능한 모든 레시피 목록을 반환합니다.
     * UI 개발자는 이 메소드를 사용하여 사용자가 선택할 수 있는 레시피 목록을 표시할 수 있습니다.
     * @return Recipe 객체의 리스트.
     */
    public static List<Recipe> getRecipes() {
        List<Recipe> recipes = new ArrayList<>();

        // 하드코딩된 라면 레시피
        Recipe ramen = new Recipe("Ramen", Arrays.asList(
                new RecipeStep("물 끓이기", 300), // 5분
                new RecipeStep("면과 스프 넣기", 240), // 4분
                new RecipeStep("계란 넣고 익히기", 60) // 1분
        ));
        recipes.add(ramen);

        // TODO: 추후에 더 많은 레시피를 여기에 추가하세요.

        return recipes;
    }

    /**
     * 빠른 테스트를 위해 기본 레시피를 반환합니다.
     * @return 기본 Recipe 객체.
     */
    public static Recipe getDefaultRecipe() {
        return getRecipes().get(0);
    }
}
