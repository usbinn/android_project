package com.example.recipealarm;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * 새로운 레시피를 추가하거나 기존 레시피를 수정하는 화면의 액티비티입니다.
 * 사용자는 이 화면에서 레시피의 이름과 각 단계를 입력할 수 있습니다.
 *
 * UI 개발자는 이 액티비티의 레이아웃(activity_add_recipe.xml)을 자유롭게 수정하여
 * 디자인을 개선할 수 있습니다. 핵심 로직은 그대로 유지됩니다.
 */
public class AddRecipeActivity extends AppCompatActivity {

    private EditText recipeNameInput;
    private LinearLayout stepsContainer;
    private Button addStepButton;
    private Button saveRecipeButton;

    private RecipeRepository recipeRepository;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_recipe);

        recipeRepository = new RecipeRepository(this);

        recipeNameInput = findViewById(R.id.recipe_name_input);
        stepsContainer = findViewById(R.id.steps_container);
        addStepButton = findViewById(R.id.add_step_button);
        saveRecipeButton = findViewById(R.id.save_recipe_button);

        addStepButton.setOnClickListener(v -> addStepView());
        saveRecipeButton.setOnClickListener(v -> saveRecipe());

        // 초기 단계 입력 필드 하나를 추가합니다.
        addStepView();
    }

    /**
     * '단계 추가' 버튼을 누르면 호출됩니다.
     * 레시피 단계를 입력할 수 있는 새로운 View를 동적으로 추가합니다.
     */
    private void addStepView() {
        LayoutInflater inflater = LayoutInflater.from(this);
        View stepView = inflater.inflate(R.layout.item_recipe_step_input, stepsContainer, false);
        stepsContainer.addView(stepView);
    }

    /**
     * '레시피 저장' 버튼을 누르면 호출됩니다.
     * 입력된 데이터를 Recipe 객체로 만들어 저장소에 저장합니다.
     */
    private void saveRecipe() {
        String recipeName = recipeNameInput.getText().toString().trim();
        if (recipeName.isEmpty()) {
            Toast.makeText(this, "레시피 이름을 입력해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<RecipeStep> steps = new ArrayList<>();
        for (int i = 0; i < stepsContainer.getChildCount(); i++) {
            View stepView = stepsContainer.getChildAt(i);
            EditText stepDescriptionInput = stepView.findViewById(R.id.step_description_input);
            EditText stepDurationInput = stepView.findViewById(R.id.step_duration_input);

            String description = stepDescriptionInput.getText().toString().trim();
            String durationStr = stepDurationInput.getText().toString().trim();

            if (description.isEmpty() || durationStr.isEmpty()) {
                Toast.makeText(this, "단계 " + (i + 1) + "의 설명과 시간을 모두 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                int durationInSeconds = Integer.parseInt(durationStr);
                steps.add(new RecipeStep(description, durationInSeconds));
            } catch (NumberFormatException e) {
                Toast.makeText(this, "단계 " + (i + 1) + "의 시간은 숫자로 입력해주세요.", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        if (steps.isEmpty()) {
            Toast.makeText(this, "레시피 단계를 하나 이상 추가해주세요.", Toast.LENGTH_SHORT).show();
            return;
        }

        Recipe newRecipe = new Recipe(recipeName, steps);
        recipeRepository.addRecipe(newRecipe).whenComplete((aVoid, throwable) -> {
            if (throwable != null) {
                runOnUiThread(() -> Toast.makeText(AddRecipeActivity.this, "저장 실패: " + throwable.getMessage(), Toast.LENGTH_SHORT).show());
            } else {
                runOnUiThread(() -> {
                    Toast.makeText(AddRecipeActivity.this, "레시피가 저장되었습니다.", Toast.LENGTH_SHORT).show();
                    finish(); // 저장이 완료되면 화면을 닫습니다.
                });
            }
        });
    }
}
