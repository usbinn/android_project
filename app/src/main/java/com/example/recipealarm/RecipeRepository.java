package com.example.recipealarm;

import android.content.Context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 레시피 데이터의 출처(Source of truth) 역할을 하는 클래스입니다.
 * UI나 비즈니스 로직은 이 클래스를 통해 레시피 데이터에 접근해야 합니다.
 * 내부적으로 RecipeDataStore를 사용하여 데이터를 로컬에 영구 저장합니다.
 *
 * UI 개발자는 이 클래스의 public 메소드를 사용하여 비동기적으로 데이터를 가져오고,
 * 레시피를 추가, 수정, 삭제하는 기능을 구현할 수 있습니다.
 */
public class RecipeRepository {

    private final RecipeDataStore dataStore;

    /**
     * 생성자. RecipeRepository 인스턴스를 생성합니다.
     * @param context 애플리케이션 컨텍스트. DataStore를 초기화하는 데 필요합니다.
     */
    public RecipeRepository(Context context) {
        this.dataStore = RecipeDataStore.getInstance(context);
    }

    /**
     * 저장된 모든 레시피 목록을 비동기적으로 가져옵니다.
     * 만약 저장된 레시피가 하나도 없다면, 샘플 레시피를 생성하여 저장하고 반환합니다.
     *
     * UI 개발자는 이 메소드를 호출하여 레시피 목록 화면을 구성할 수 있습니다.
     * CompletableFuture를 사용하므로, 결과를 받은 후 UI를 업데이트해야 합니다.
     *
     * @return 레시피 리스트를 담고 있는 CompletableFuture.
     */
    public CompletableFuture<List<Recipe>> getRecipes() {
        return dataStore.getRecipes().thenCompose(recipes -> {
            if (recipes == null || recipes.isEmpty()) {
                // 데이터가 없으면 샘플 레시피를 생성하고 저장합니다.
                return createSampleRecipes().thenCompose(sampleRecipes -> dataStore.saveRecipes(sampleRecipes).thenApply(v -> sampleRecipes));
            }
            return CompletableFuture.completedFuture(recipes);
        });
    }

    /**
     * ID를 이용해 특정 레시피 하나를 비동기적으로 가져옵니다.
     * @param recipeId 가져올 레시피의 고유 ID
     * @return 해당 레시피 객체를 담은 CompletableFuture. 레시피가 없으면 null을 담고 있습니다.
     */
    public CompletableFuture<Recipe> getRecipeById(String recipeId) {
        return getRecipes().thenApply(recipes ->
                recipes.stream()
                        .filter(r -> Objects.equals(r.getId(), recipeId))
                        .findFirst()
                        .orElse(null)
        );
    }

    /**
     * 새로운 레시피를 목록에 추가하고 저장합니다.
     * @param recipe 추가할 Recipe 객체.
     * @return 저장이 완료되면 끝나는 CompletableFuture.
     */
    public CompletableFuture<Void> addRecipe(Recipe recipe) {
        return getRecipes().thenCompose(recipes -> {
            recipes.add(recipe);
            return dataStore.saveRecipes(recipes);
        });
    }

    /**
     * 기존 레시피의 정보를 업데이트합니다.
     * @param updatedRecipe 업데이트할 정보가 담긴 Recipe 객체. ID가 동일해야 합니다.
     * @return 업데이트가 완료되면 끝나는 CompletableFuture.
     */
    public CompletableFuture<Void> updateRecipe(Recipe updatedRecipe) {
        return getRecipes().thenCompose(recipes -> {
            List<Recipe> newRecipeList = recipes.stream()
                    .map(r -> Objects.equals(r.getId(), updatedRecipe.getId()) ? updatedRecipe : r)
                    .collect(Collectors.toList());
            return dataStore.saveRecipes(newRecipeList);
        });
    }

    /**
     * ID를 이용해 특정 레시피를 삭제합니다.
     * @param recipeId 삭제할 레시피의 고유 ID.
     * @return 삭제가 완료되면 끝나는 CompletableFuture.
     */
    public CompletableFuture<Void> deleteRecipe(String recipeId) {
        return getRecipes().thenCompose(recipes -> {
            List<Recipe> newRecipeList = recipes.stream()
                    .filter(r -> !Objects.equals(r.getId(), recipeId))
                    .collect(Collectors.toList());
            return dataStore.saveRecipes(newRecipeList);
        });
    }

    /**
     * 샘플 레시피 데이터를 생성합니다. 앱 최초 실행 시 사용됩니다.
     * @return 샘플 레시피 리스트를 담은 CompletableFuture.
     */
    private CompletableFuture<List<Recipe>> createSampleRecipes() {
        return CompletableFuture.supplyAsync(() -> {
            List<Recipe> sampleRecipes = new ArrayList<>();
            Recipe ramen = new Recipe("신라면 맛있게 끓이기", Arrays.asList(
                    new RecipeStep("물 550ml 끓이기", 180), // 3분
                    new RecipeStep("면과 분말, 건더기 스프 넣기", 270), // 4분 30초
                    new RecipeStep("계란 넣고 30초 더 끓이기", 30) // 30초
            ));
            ramen.setFavorite(true); // 샘플 레시피를 즐겨찾기에 추가해 봅니다.
            sampleRecipes.add(ramen);
            return sampleRecipes;
        });
    }
}
