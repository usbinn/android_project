package com.example.recipealarm;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.recipealarm.utils.Constants;
import com.example.recipealarm.utils.PermissionHelper;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.gson.Gson;

/**
 * 레시피 타이머 화면을 표시하는 액티비티
 * UI 표시와 사용자 입력 처리만 담당하며, 모든 타이머 로직은 TimerService로 위임합니다.
 */
public class RecipeActivity extends AppCompatActivity {

    private static final String TAG = "RecipeActivity";

    private RecipeRepository recipeRepository;
    private Recipe currentRecipe;
    private boolean isPaused = false;

    // UI 컴포넌트
    private TextView currentStepTitle;
    private TextView currentStepIndex;
    private TextView currentStepTimer;
    private CircularProgressIndicator circleTimer;
    private MaterialButton buttonPausePlay;
    private MaterialButton buttonPrevStep;
    private MaterialButton buttonNextStep;

    // BroadcastReceiver
    private BroadcastReceiver timerUpdateReceiver;

    // 권한 요청
    private ActivityResultLauncher<String[]> requestPermissionsLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cooking_timer);

        initializeViews();
        setupToolbar();
        setupPermissions();
        setupTimerReceiver();

        String recipeId = getIntent().getStringExtra(Constants.EXTRA_RECIPE_ID);
        if (recipeId == null || recipeId.isEmpty()) {
            showErrorAndFinish("레시피 정보를 불러올 수 없습니다.");
            return;
        }

        recipeRepository = new RecipeRepository(getApplicationContext());
        loadAndStartRecipe(recipeId);
    }

    private void initializeViews() {
        currentStepTitle = findViewById(R.id.current_step_title);
        currentStepIndex = findViewById(R.id.current_step_index);
        currentStepTimer = findViewById(R.id.current_step_timer);
        circleTimer = findViewById(R.id.circle_timer);
        buttonPausePlay = findViewById(R.id.button_pause_play);
        buttonPrevStep = findViewById(R.id.button_prev_step);
        buttonNextStep = findViewById(R.id.button_next_step);

        // 버튼 클릭 리스너 설정
        if (buttonPausePlay != null) {
            buttonPausePlay.setOnClickListener(v -> toggleTimer());
        }
        if (buttonPrevStep != null) {
            buttonPrevStep.setOnClickListener(v -> navigateToPrevStep());
        }
        if (buttonNextStep != null) {
            buttonNextStep.setOnClickListener(v -> navigateToNextStep());
        }
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = findViewById(R.id.toolbar_timer);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
            toolbar.setNavigationOnClickListener(v -> stopCurrentRecipe());
        }
    }

    private void setupPermissions() {
        requestPermissionsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {
                    boolean allGranted = permissions.values().stream().allMatch(Boolean::booleanValue);
                    if (!allGranted) {
                        Toast.makeText(this, "일부 권한이 거부되었습니다. 일부 기능이 제한될 수 있습니다.", Toast.LENGTH_SHORT).show();
                    }
                });

        requestRuntimePermissions();
        checkExactAlarmPermission();
    }

    private void requestRuntimePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!PermissionHelper.hasPermissions(this, Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.RECORD_AUDIO)) {
                requestPermissionsLauncher.launch(new String[]{
                        Manifest.permission.POST_NOTIFICATIONS,
                        Manifest.permission.RECORD_AUDIO
                });
            }
        } else {
            if (!PermissionHelper.hasPermissions(this, Manifest.permission.RECORD_AUDIO)) {
                requestPermissionsLauncher.launch(new String[]{Manifest.permission.RECORD_AUDIO});
            }
        }
    }

    private void checkExactAlarmPermission() {
        PermissionHelper.checkExactAlarmPermission(this, null, null);
    }

    private void setupTimerReceiver() {
        timerUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action == null) return;

                String recipeId = intent.getStringExtra(TimerService.EXTRA_RECIPE_ID);
                if (currentRecipe == null || !currentRecipe.getId().equals(recipeId)) {
                    return;
                }

                if (Constants.ACTION_TIMER_UPDATE.equals(action)) {
                    handleTimerUpdate(intent);
                } else if (Constants.ACTION_TIMER_FINISH.equals(action)) {
                    handleTimerFinish();
                }
            }
        };
    }

    private void handleTimerUpdate(Intent intent) {
        String stepDescription = intent.getStringExtra(Constants.EXTRA_STEP_DESCRIPTION);
        String timeRemaining = intent.getStringExtra(Constants.EXTRA_TIME_REMAINING_FORMATTED);
        int stepIndex = intent.getIntExtra(Constants.EXTRA_STEP_INDEX, 0);
        int totalSteps = intent.getIntExtra(Constants.EXTRA_TOTAL_STEPS, 0);
        long timeRemainingMs = intent.getLongExtra(Constants.EXTRA_TIME_REMAINING_MS, 0);
        long stepDurationMs = intent.getLongExtra(Constants.EXTRA_STEP_DURATION_MS, 1);
        isPaused = intent.getBooleanExtra(Constants.EXTRA_IS_PAUSED, false);

        updateTimerUI(stepDescription, timeRemaining, stepIndex, totalSteps, timeRemainingMs, stepDurationMs);
    }

    private void handleTimerFinish() {
        if (currentRecipe != null) {
            // 완료 화면으로 이동
            Intent intent = new Intent(this, RecipeCompleteActivity.class);
            intent.putExtra(Constants.EXTRA_RECIPE_ID, currentRecipe.getId());
            intent.putExtra(RecipeCompleteActivity.EXTRA_RECIPE_NAME, currentRecipe.getName());
            startActivity(intent);
        }
        finish();
    }

    private void loadAndStartRecipe(String recipeId) {
        recipeRepository.getRecipeById(recipeId)
                .thenAccept(recipe -> {
                    if (recipe == null) {
                        runOnUiThread(() -> showErrorAndFinish("레시피를 찾을 수 없습니다."));
                        return;
                    }

                    this.currentRecipe = recipe;
                    startTimerService(recipe);
                    
                    runOnUiThread(() -> {
                        setTitle(recipe.getName());
                        resetProgress();
                        updatePauseButton(false);
                        Toast.makeText(this, "레시피 시작: " + recipe.getName(), Toast.LENGTH_SHORT).show();
                    });
                })
                .exceptionally(ex -> {
                    Log.e(TAG, "레시피 로드 실패", ex);
                    runOnUiThread(() -> showErrorAndFinish("레시피를 불러오는 데 실패했습니다."));
                    return null;
                });
    }

    private void startTimerService(Recipe recipe) {
        Intent serviceIntent = new Intent(this, TimerService.class);
        serviceIntent.setAction(Constants.ACTION_START_TIMER);
        serviceIntent.putExtra(RecipeTimer.EXTRA_RECIPE_JSON, new Gson().toJson(recipe));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
    }

    private void updateTimerUI(String stepDescription, String timeRemaining, int stepIndex,
                               int totalSteps, long timeRemainingMs, long stepDurationMs) {
        setTextSafely(currentStepTitle, "[" + (stepIndex + 1) + "단계] " + stepDescription);
        setTextSafely(currentStepIndex, (stepIndex + 1) + " / " + totalSteps + " 단계");
        setTextSafely(currentStepTimer, timeRemaining);

        // 프로그레스 업데이트
        if (circleTimer != null && stepDurationMs > 0) {
            try {
                int progress = (int) ((stepDurationMs - timeRemainingMs) * 100 / stepDurationMs);
                circleTimer.setProgress(Math.max(0, Math.min(100, progress)));
            } catch (Exception e) {
                Log.e(TAG, "Progress 업데이트 실패", e);
            }
        }

        // 버튼 상태 업데이트
        setEnabledSafely(buttonPrevStep, stepIndex > 0 && !isPaused);
        setEnabledSafely(buttonNextStep, stepIndex < totalSteps - 1 && !isPaused);
        updatePauseButton(isPaused);
    }

    private void updatePauseButton(boolean paused) {
        if (buttonPausePlay != null) {
            if (paused) {
                buttonPausePlay.setText("재생");
            } else {
                buttonPausePlay.setText("일시정지");
            }
        }
    }

    private void resetProgress() {
        if (circleTimer != null) {
            circleTimer.setProgress(0);
        }
    }

    // 안전한 UI 업데이트 헬퍼 메서드들
    private void setTextSafely(TextView textView, String text) {
        if (textView != null) {
            textView.setText(text);
        }
    }

    private void setTextSafely(MaterialButton button, String text) {
        if (button != null) {
            button.setText(text);
        }
    }

    private void setEnabledSafely(MaterialButton button, boolean enabled) {
        if (button != null) {
            button.setEnabled(enabled);
        }
    }

    private void showErrorAndFinish(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        finish();
    }

    private void toggleTimer() {
        if (currentRecipe == null) return;

        Intent serviceIntent = new Intent(this, TimerService.class);
        serviceIntent.putExtra(TimerService.EXTRA_RECIPE_ID, currentRecipe.getId());

        if (isPaused) {
            serviceIntent.setAction(Constants.ACTION_RESUME_TIMER);
        } else {
            serviceIntent.setAction(Constants.ACTION_PAUSE_TIMER);
        }

        startService(serviceIntent);
    }

    private void navigateToPrevStep() {
        if (currentRecipe == null || isPaused) return;

        Intent serviceIntent = new Intent(this, TimerService.class);
        serviceIntent.setAction(Constants.ACTION_NAVIGATE_STEP);
        serviceIntent.putExtra(TimerService.EXTRA_RECIPE_ID, currentRecipe.getId());
        serviceIntent.putExtra(Constants.EXTRA_NAVIGATE_DIRECTION, "prev");
        startService(serviceIntent);
    }

    private void navigateToNextStep() {
        if (currentRecipe == null || isPaused) return;

        Intent serviceIntent = new Intent(this, TimerService.class);
        serviceIntent.setAction(Constants.ACTION_NAVIGATE_STEP);
        serviceIntent.putExtra(TimerService.EXTRA_RECIPE_ID, currentRecipe.getId());
        serviceIntent.putExtra(Constants.EXTRA_NAVIGATE_DIRECTION, "next");
        startService(serviceIntent);
    }

    private void stopCurrentRecipe() {
        if (currentRecipe == null) {
            Toast.makeText(this, "실행 중인 레시피가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent serviceIntent = new Intent(this, TimerService.class);
        serviceIntent.setAction(Constants.ACTION_STOP_TIMER);
        serviceIntent.putExtra(TimerService.EXTRA_RECIPE_ID, currentRecipe.getId());
        startService(serviceIntent);

        Toast.makeText(this, "레시피가 취소되었습니다.", Toast.LENGTH_SHORT).show();
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        PermissionHelper.checkExactAlarmPermission(this, null, null);
        
        IntentFilter filter = new IntentFilter();
        filter.addAction(Constants.ACTION_TIMER_UPDATE);
        filter.addAction(Constants.ACTION_TIMER_FINISH);
        LocalBroadcastManager.getInstance(this).registerReceiver(timerUpdateReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(timerUpdateReceiver);
    }
}
