package com.example.recipealarm;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.example.recipealarm.utils.Constants;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;

/**
 * 레시피 요리가 완료되었을 때 표시되는 완료 화면
 */
public class RecipeCompleteActivity extends AppCompatActivity {

    public static final String EXTRA_RECIPE_NAME = "EXTRA_RECIPE_NAME";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe_complete);

        setupToolbar();
        setupViews();

        MaterialButton backButton = findViewById(R.id.button_back_to_list);
        if (backButton != null) {
            backButton.setOnClickListener(v -> navigateToRecipeList());
        }
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar_complete);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(false);
            }
        }
    }

    private void setupViews() {
        String recipeName = getIntent().getStringExtra(EXTRA_RECIPE_NAME);
        
        android.widget.TextView recipeNameText = findViewById(R.id.complete_recipe_name);
        if (recipeNameText != null && recipeName != null) {
            recipeNameText.setText(recipeName);
        }
    }

    private void navigateToRecipeList() {
        Intent intent = new Intent(this, RecipeListActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    public void onBackPressed() {
        navigateToRecipeList();
    }
}

