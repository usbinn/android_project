package com.example.recipealarm;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.recipealarm.utils.Constants;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import java.util.ArrayList;
import java.util.List;

/**
 * 레시피 상세 정보를 보여주는 액티비티입니다.
 * 사용자는 이 화면에서 레시피의 모든 단계를 확인하고, "요리 시작" 버튼을 눌러 타이머를 시작할 수 있습니다.
 */
public class RecipeDetailActivity extends AppCompatActivity {

    private RecipeRepository recipeRepository;
    private Recipe currentRecipe;
    private RecipeStepAdapter stepAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe_detail);

        // Toolbar 설정
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar_detail);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        recipeRepository = new RecipeRepository(this);

        String recipeId = getIntent().getStringExtra(Constants.EXTRA_RECIPE_ID);
        if (recipeId == null || recipeId.isEmpty()) {
            Toast.makeText(this, "레시피 정보를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // RecyclerView 설정
        RecyclerView stepsRecyclerView = findViewById(R.id.detail_steps_recycler_view);
        stepsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        stepAdapter = new RecipeStepAdapter();
        stepsRecyclerView.setAdapter(stepAdapter);

        // "요리 시작" 버튼 설정
        ExtendedFloatingActionButton fabStartCooking = findViewById(R.id.fab_start_cooking);
        fabStartCooking.setOnClickListener(v -> startCooking());

        loadRecipe(recipeId);
    }

    /**
     * 레시피 정보를 불러와 화면에 표시합니다.
     */
    private void loadRecipe(String recipeId) {
        recipeRepository.getRecipeById(recipeId).thenAccept(recipe -> {
            if (recipe == null) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "레시피를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
                    finish();
                });
                return;
            }

            this.currentRecipe = recipe;

            runOnUiThread(() -> {
                // 레시피 제목 표시
                TextView titleText = findViewById(R.id.detail_recipe_title);
                if (titleText != null) {
                    titleText.setText(recipe.getName());
                }

                // 총 시간과 단계 수 계산 및 표시
                int totalSeconds = 0;
                int stepCount = recipe.getSteps().size();
                for (RecipeStep step : recipe.getSteps()) {
                    totalSeconds += step.getDurationInSeconds();
                }
                int totalMinutes = totalSeconds / 60;

                TextView totalTimeText = findViewById(R.id.detail_total_time);
                if (totalTimeText != null) {
                    totalTimeText.setText("총 " + totalMinutes + "분 · " + stepCount + "단계");
                }

                // 단계 목록 표시
                stepAdapter.setSteps(recipe.getSteps());

                // Toolbar 제목 업데이트
                setTitle(recipe.getName());
            });

        }).exceptionally(ex -> {
            runOnUiThread(() -> {
                Toast.makeText(this, "레시피를 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show();
                finish();
            });
            return null;
        });
    }

    /**
     * "요리 시작" 버튼을 눌렀을 때 호출됩니다.
     * 타이머 화면으로 이동합니다.
     */
    private void startCooking() {
        if (currentRecipe == null) {
            Toast.makeText(this, "레시피 정보를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent intent = new Intent(this, RecipeActivity.class);
        intent.putExtra(Constants.EXTRA_RECIPE_ID, currentRecipe.getId());
        startActivity(intent);
    }

    /**
     * 레시피 단계 목록을 표시하기 위한 어댑터입니다.
     */
    private static class RecipeStepAdapter extends androidx.recyclerview.widget.RecyclerView.Adapter<RecipeStepAdapter.StepViewHolder> {

        private List<RecipeStep> steps = new ArrayList<>();

        public void setSteps(List<RecipeStep> steps) {
            this.steps = steps != null ? steps : new ArrayList<>();
            notifyDataSetChanged();
        }

        @Override
        public StepViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View view = android.view.LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.cooking_step_list_item, parent, false);
            return new StepViewHolder(view);
        }

        @Override
        public void onBindViewHolder(StepViewHolder holder, int position) {
            RecipeStep step = steps.get(position);
            holder.bind(step, position);
        }

        @Override
        public int getItemCount() {
            return steps.size();
        }

        static class StepViewHolder extends androidx.recyclerview.widget.RecyclerView.ViewHolder {
            private final TextView stepNumber;
            private final TextView stepTitle;
            private final TextView stepTime;

            public StepViewHolder(View itemView) {
                super(itemView);
                stepNumber = itemView.findViewById(R.id.step_number);
                stepTitle = itemView.findViewById(R.id.step_item_title);
                stepTime = itemView.findViewById(R.id.step_item_time);
            }

            public void bind(RecipeStep step, int position) {
                stepNumber.setText(String.valueOf(position + 1));
                stepTitle.setText(step.getDescription());

                // 시간 포맷팅 (초 -> 분:초)
                int minutes = step.getDurationInSeconds() / 60;
                int seconds = step.getDurationInSeconds() % 60;
                stepTime.setText(String.format("%02d:%02d", minutes, seconds));
            }
        }
    }
}


