package com.example.recipealarm;

import android.content.Context;
import android.util.Log;

import androidx.datastore.core.DataStore;
import androidx.datastore.preferences.core.MutablePreferences;
import androidx.datastore.preferences.core.PreferenceDataStoreFactory;
import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;
import androidx.datastore.preferences.core.PreferencesKt;
import androidx.datastore.preferences.preferences.PreferencesSerializer;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.File;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import kotlinx.coroutines.flow.Flow;
import kotlinx.coroutines.flow.first;
import kotlinx.coroutines.flow.map;
import kotlinx.coroutines.future.FutureKt;

/**
 * DataStore를 사용하여 레시피 목록을 로컬에 저장하고 관리하는 클래스입니다.
 * 앱 전체에서 하나의 인스턴스만 사용하도록 싱글톤으로 구현되었습니다.
 * 모든 데이터 I/O는 비동기적으로 처리됩니다.
 */
public class RecipeDataStore {

    private static final String TAG = "RecipeDataStore";
    private static final String DATA_STORE_FILE_NAME = "recipe_store.preferences_pb";
    private static volatile RecipeDataStore INSTANCE;

    private final DataStore<Preferences> dataStore;
    private final Gson gson = new Gson();
    private final Executor executor = Executors.newSingleThreadExecutor();

    // DataStore에 데이터를 저장할 때 사용할 키
    private static final Preferences.Key<String> RECIPES_KEY = PreferencesKeys.stringKey("recipes_json");

    private RecipeDataStore(Context context) {
        this.dataStore = PreferenceDataStoreFactory.create(
                (PreferencesSerializer) PreferencesSerializer.getDEFAULT_INSTANCE(),
                () -> new File(context.getFilesDir(), "datastore/" + DATA_STORE_FILE_NAME),
                Executors.newSingleThreadExecutor()
        );
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
     * DataStore에 저장된 레시피 목록을 비동기적으로 불러옵니다.
     * @return 레시피 리스트를 담고 있는 CompletableFuture. 데이터가 없으면 빈 리스트를 반환합니다.
     */
    public CompletableFuture<List<Recipe>> getRecipes() {
        Flow<String> jsonFlow = dataStore.data().map(prefs -> prefs.get(RECIPES_KEY));
        
        return FutureKt.future(
                executor.getCoroutineContext(),
                (scope, continuation) -> {
                    try {
                        String json = first(jsonFlow, continuation);
                        if (json == null || json.isEmpty()) {
                            return new ArrayList<>();
                        }
                        Type type = new TypeToken<ArrayList<Recipe>>() {}.getType();
                        return gson.fromJson(json, type);
                    } catch (Exception e) {
                        Log.e(TAG, "Error getting recipes from DataStore", e);
                        return new ArrayList<>(); // 오류 발생 시 빈 리스트 반환
                    }
                }
        );
    }

    /**
     * 레시피 목록 전체를 DataStore에 비동기적으로 저장합니다.
     * @param recipes 저장할 레시피 목록
     * @return 저장이 완료되면 끝나는 CompletableFuture
     */
    public CompletableFuture<Void> saveRecipes(List<Recipe> recipes) {
        String json = gson.toJson(recipes);
        ListenableFuture<Preferences> updateFuture = dataStore.updateDataAsync(prefsIn -> {
            MutablePreferences mutablePreferences = prefsIn.toMutablePreferences();
            mutablePreferences.set(RECIPES_KEY, json);
            return com.google.common.util.concurrent.Futures.immediateFuture(mutablePreferences.toPreferences());
        });

        CompletableFuture<Void> completableFuture = new CompletableFuture<>();
        updateFuture.addListener(() -> {
            try {
                updateFuture.get();
                completableFuture.complete(null);
            } catch (Exception e) {
                Log.e(TAG, "Error saving recipes to DataStore", e);
                completableFuture.completeExceptionally(e);
            }
        }, executor);

        return completableFuture;
    }
}
