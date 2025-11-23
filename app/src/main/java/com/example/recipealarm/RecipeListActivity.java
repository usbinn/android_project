package com.example.recipealarm;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
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
    private View emptyStateView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe_list);

        recipeRepository = new RecipeRepository(this);

        // Toolbar 설정
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // RecyclerView 설정
        RecyclerView recyclerView = findViewById(R.id.recipe_recycler_view);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recipeAdapter = new RecipeAdapter(this, this);
        recyclerView.setAdapter(recipeAdapter);

        // 빈 상태 뷰 설정
        emptyStateView = findViewById(R.id.empty_state_view);

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
            runOnUiThread(() -> {
                if (throwable != null) {
                    Toast.makeText(this, "레시피를 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show();
                    updateEmptyState(recipes != null ? recipes : java.util.Collections.emptyList());
                } else {
                    recipeAdapter.setRecipes(recipes);
                    updateEmptyState(recipes);
                }
            });
        });
    }

    /**
     * 레시피 목록이 비어있을 때 빈 상태 뷰를 표시합니다.
     */
    private void updateEmptyState(List<Recipe> recipes) {
        if (emptyStateView != null) {
            boolean isEmpty = recipes == null || recipes.isEmpty();
            emptyStateView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * 레시피 목록에서 항목이 클릭되었을 때 호출됩니다.
     * @param recipe 클릭된 Recipe 객체
     */
    @Override
    public void onRecipeClick(Recipe recipe) {
        Intent intent = new Intent(this, RecipeDetailActivity.class);
        intent.putExtra(RecipeDetailActivity.EXTRA_RECIPE_ID, recipe.getId());
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
