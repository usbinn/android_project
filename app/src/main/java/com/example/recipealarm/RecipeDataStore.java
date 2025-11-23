package com.example.recipealarm;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * SharedPreferences를 사용하여 레시피 목록을 로컬에 저장하고 관리하는 클래스입니다.
 * 앱 전체에서 하나의 인스턴스만 사용하도록 싱글톤으로 구현되었습니다.
 * 모든 데이터 I/O는 비동기적으로 처리됩니다.
 */
public class RecipeDataStore {

    private static final String TAG = "RecipeDataStore";
    private static final String PREFS_NAME = "recipe_store";
    private static final String RECIPES_KEY = "recipes_json";
    private static volatile RecipeDataStore INSTANCE;

    private final SharedPreferences sharedPreferences;
    private final Gson gson = new Gson();
    private final Executor executor = Executors.newSingleThreadExecutor();

    private RecipeDataStore(Context context) {
        this.sharedPreferences = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * RecipeDataStore의 싱글톤 인스턴스를 가져옵니다.
     * @param context 애플리케이션 컨텍스트
     * @return RecipeDataStore 인스턴스
     */
    public static RecipeDataStore getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (RecipeDataStore.class) {
                if (INSTANCE == null) {
                    INSTANCE = new RecipeDataStore(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    /**
     * SharedPreferences에 저장된 레시피 목록을 비동기적으로 불러옵니다.
     * @return 레시피 리스트를 담고 있는 CompletableFuture. 데이터가 없으면 빈 리스트를 반환합니다.
     */
    public CompletableFuture<List<Recipe>> getRecipes() {
        CompletableFuture<List<Recipe>> future = new CompletableFuture<>();
        
        executor.execute(() -> {
            try {
                String json = sharedPreferences.getString(RECIPES_KEY, null);
                if (json == null || json.isEmpty()) {
                    future.complete(new ArrayList<>());
                    return;
                }
                
                Type type = new TypeToken<ArrayList<Recipe>>() {}.getType();
                List<Recipe> recipes = gson.fromJson(json, type);
                future.complete(recipes != null ? recipes : new ArrayList<>());
            } catch (Exception e) {
                Log.e(TAG, "Error getting recipes from DataStore", e);
                future.complete(new ArrayList<>());
            }
        });
        
        return future;
    }

    /**
     * 레시피 목록 전체를 SharedPreferences에 비동기적으로 저장합니다.
     * @param recipes 저장할 레시피 목록
     * @return 저장이 완료되면 끝나는 CompletableFuture
     */
    public CompletableFuture<Void> saveRecipes(List<Recipe> recipes) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        String json = gson.toJson(recipes);
        
        executor.execute(() -> {
            try {
                sharedPreferences.edit()
                        .putString(RECIPES_KEY, json)
                        .apply();
                future.complete(null);
            } catch (Exception e) {
                Log.e(TAG, "Error saving recipes to DataStore", e);
                future.completeExceptionally(e);
            }
        });
        
        return future;
    }
}
