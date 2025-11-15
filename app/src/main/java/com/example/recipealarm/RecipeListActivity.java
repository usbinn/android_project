package com.example.recipealarm;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.List;

/**
 * 앱의 메인 화면으로, 저장된 레시피 목록을 보여주는 액티비티입니다.
 * 사용자는 이 화면에서 레시피를 선택하여 타이머를 시작하거나,
 * 새로운 레시피를 추가하거나, 즐겨찾기를 관리할 수 있습니다.
 * 이 액티비티가 앱의 시작점(Launcher Activity)이 됩니다.
 */
public class RecipeListActivity extends AppCompatActivity implements RecipeAdapter.OnRecipeClickListener, RecipeAdapter.OnFavoriteClickListener {

    private RecipeRepository recipeRepository;
    private RecipeAdapter recipeAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe_list);

        recipeRepository = new RecipeRepository(this);

        // RecyclerView 설정
        RecyclerView recyclerView = findViewById(R.id.recipe_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recipeAdapter = new RecipeAdapter(this, this);
        recyclerView.setAdapter(recipeAdapter);

        // '레시피 추가' 버튼 설정
        FloatingActionButton fab = findViewById(R.id.fab_add_recipe);
        fab.setOnClickListener(view -> {
            Intent intent = new Intent(RecipeListActivity.this, AddRecipeActivity.class);
            startActivity(intent);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadRecipes();
    }

    /**
     * RecipeRepository에서 레시피 목록을 불러와 RecyclerView에 표시합니다.
     */
    private void loadRecipes() {
        recipeRepository.getRecipes().whenComplete((recipes, throwable) -> {
            if (throwable != null) {
                runOnUiThread(() -> Toast.makeText(this, "레시피를 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show());
            } else {
                runOnUiThread(() -> recipeAdapter.setRecipes(recipes));
            }
        });
    }

    /**
     * 레시피 목록에서 항목이 클릭되었을 때 호출됩니다.
     * @param recipe 클릭된 Recipe 객체
     */
    @Override
    public void onRecipeClick(Recipe recipe) {
        Intent intent = new Intent(this, RecipeActivity.class);
        intent.putExtra(RecipeActivity.EXTRA_RECIPE_ID, recipe.getId());
        startActivity(intent);
    }

    /**
     * 즐겨찾기 아이콘이 클릭되었을 때 호출됩니다.
     * @param recipe 클릭된 Recipe 객체
     */
    @Override
    public void onFavoriteClick(Recipe recipe) {
        recipe.setFavorite(!recipe.isFavorite());
        recipeRepository.updateRecipe(recipe).whenComplete((aVoid, throwable) -> {
            if (throwable != null) {
                runOnUiThread(() -> Toast.makeText(this, "즐겨찾기 업데이트 실패", Toast.LENGTH_SHORT).show());
            } else {
                // 즐겨찾기 상태가 변경되었으므로 목록을 새로고침합니다.
                loadRecipes();
            }
        });
    }
}
