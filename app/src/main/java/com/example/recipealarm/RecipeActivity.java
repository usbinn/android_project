package com.example.recipealarm;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.google.gson.Gson;

import java.util.Objects;

/**
 * 이 액티비티는 이제 UI 표시와 사용자 입력 처리만 담당합니다.
 * 모든 타이머 로직은 {@link TimerService}로 위임되었습니다.
 *
 * UI 개발자는 이 액티비티를 참고하여, Service와 BroadcastReceiver를 통해
 * 백그라운드 작업과 UI를 어떻게 분리하고 연결하는지 파악할 수 있습니다.
 *
 * 새로운 아키텍처:
 * 1. 사용자 입력 (시작/취소 버튼) -> Intent를 통해 TimerService에 명령 전달
 * 2. TimerService (백그라운드 작업) -> LocalBroadcastManager를 통해 UI 업데이트 정보 방송
 * 3. RecipeActivity (UI) -> BroadcastReceiver로 정보를 수신하여 화면 업데이트
 */
public class RecipeActivity extends AppCompatActivity implements VoiceCommandHandler.VoiceCommandCallback {

    public static final String EXTRA_RECIPE_ID = "com.example.recipealarm.EXTRA_RECIPE_ID";

    private VoiceCommandHandler voiceCommandHandler;
    private RecipeRepository recipeRepository;

    private TextView stepTextView;
    private TextView timerTextView;

    // UI가 현재 표시하고 있는 레시피. 취소 시 어떤 타이머를 중지할지 식별하는 데 사용됩니다.
    private Recipe currentRecipe;

    // TimerService로부터 UI 업데이트 정보를 수신하는 BroadcastReceiver
    private BroadcastReceiver timerUpdateReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe);

        voiceCommandHandler = new VoiceCommandHandler(this, this);
        recipeRepository = new RecipeRepository(getApplicationContext());

        stepTextView = findViewById(R.id.stepTextView);
        timerTextView = findViewById(R.id.timerTextView);
        Button buttonCancel = findViewById(R.id.buttonCancel);
        Button buttonVoice = findViewById(R.id.buttonVoice);

        buttonCancel.setOnClickListener(v -> stopCurrentRecipe());
        buttonVoice.setOnClickListener(v -> voiceCommandHandler.startListening());

        setupTimerReceiver();
        askForPermissions();

        String recipeId = getIntent().getStringExtra(EXTRA_RECIPE_ID);
        if (recipeId == null || recipeId.isEmpty()) {
            Toast.makeText(this, "레시피 정보를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        loadAndStartRecipe(recipeId);
    }

    private void loadAndStartRecipe(String recipeId) {
        recipeRepository.getRecipeById(recipeId).thenAccept(recipe -> {
            if (recipe == null) {
                runOnUiThread(() -> {
                    Toast.makeText(this, "레시피를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show();
                    finish();
                });
                return;
            }

            this.currentRecipe = recipe;

            Intent serviceIntent = new Intent(this, TimerService.class);
            serviceIntent.setAction(TimerService.ACTION_START_TIMER);
            serviceIntent.putExtra(RecipeTimer.EXTRA_RECIPE_JSON, new Gson().toJson(currentRecipe));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }

            runOnUiThread(() -> {
                setTitle(currentRecipe.getName()); // 액티비티의 타이틀을 레시피 이름으로 설정
                Toast.makeText(this, "레시피 시작: " + currentRecipe.getName(), Toast.LENGTH_SHORT).show();
            });

        }).exceptionally(ex -> {
            runOnUiThread(() -> {
                Toast.makeText(this, "레시피를 불러오는 데 실패했습니다.", Toast.LENGTH_SHORT).show();
                finish();
            });
            return null;
        });
    }

    private void setupTimerReceiver() {
        timerUpdateReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action == null) return;

                String recipeId = intent.getStringExtra(TimerService.EXTRA_RECIPE_ID);

                // 현재 UI가 보고 있는 레시피에 대한 업데이트인지 확인합니다.
                if (currentRecipe == null || !Objects.equals(recipeId, currentRecipe.getId())) {
                    return;
                }

                if (action.equals(TimerService.ACTION_TIMER_UPDATE)) {
                    String stepDescription = intent.getStringExtra(TimerService.EXTRA_STEP_DESCRIPTION);
                    String timeRemaining = intent.getStringExtra(TimerService.EXTRA_TIME_REMAINING_FORMATTED);
                    stepTextView.setText(stepDescription);
                    timerTextView.setText(timeRemaining);
                } else if (action.equals(TimerService.ACTION_TIMER_FINISH)) {
                    Toast.makeText(context, "레시피가 종료되었습니다.", Toast.LENGTH_SHORT).show();
                    resetUi();
                }
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(TimerService.ACTION_TIMER_UPDATE);
        filter.addAction(TimerService.ACTION_TIMER_FINISH);
        LocalBroadcastManager.getInstance(this).registerReceiver(timerUpdateReceiver, filter);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(timerUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (voiceCommandHandler != null) {
            voiceCommandHandler.destroy();
        }
    }

    @Override
    public void onCommandReceived(String command) {
        if (command.equalsIgnoreCase("cancel")) {
            stopCurrentRecipe();
        }
    }

    /**
     * 현재 UI에 표시된 레시피의 타이머를 중지하도록 TimerService에 요청합니다.
     */
    private void stopCurrentRecipe() {
        if (currentRecipe == null) {
            Toast.makeText(this, "실행 중인 레시피가 없습니다.", Toast.LENGTH_SHORT).show();
            return;
        }

        Intent serviceIntent = new Intent(this, TimerService.class);
        serviceIntent.setAction(TimerService.ACTION_STOP_TIMER);
        // 어떤 레시피를 중지할지 ID를 명시하여 전달합니다.
        serviceIntent.putExtra(TimerService.EXTRA_RECIPE_ID, currentRecipe.getId());
        startService(serviceIntent);

        Toast.makeText(this, "레시피가 취소되었습니다.", Toast.LENGTH_SHORT).show();
        finish(); // 취소 후 화면을 닫습니다.
    }

    private void askForPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionsLauncher.launch(new String[]{
                        Manifest.permission.POST_NOTIFICATIONS,
                        Manifest.permission.RECORD_AUDIO
                });
            }
        } else {
             if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionsLauncher.launch(new String[]{Manifest.permission.RECORD_AUDIO});
            }
        }
    }
}
