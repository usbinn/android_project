package com.example.recipealarm;

import android.Manifest;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
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

    private TextView currentStepTitle;
    private TextView currentStepIndex;
    private TextView currentStepTimer;
    private com.google.android.material.progressindicator.CircularProgressIndicator circleTimer;
    private com.google.android.material.button.MaterialButton buttonPausePlay;
    private com.google.android.material.button.MaterialButton buttonPrevStep;
    private com.google.android.material.button.MaterialButton buttonNextStep;

    // UI가 현재 표시하고 있는 레시피. 취소 시 어떤 타이머를 중지할지 식별하는 데 사용됩니다.
    private Recipe currentRecipe;

    // TimerService로부터 UI 업데이트 정보를 수신하는 BroadcastReceiver
    private BroadcastReceiver timerUpdateReceiver;

    // 권한 요청을 위한 ActivityResultLauncher
    private ActivityResultLauncher<String[]> requestPermissionsLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cooking_timer);

        // Toolbar 설정
        com.google.android.material.appbar.MaterialToolbar toolbar = findViewById(R.id.toolbar_timer);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) {
                getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            }
            toolbar.setNavigationOnClickListener(v -> stopCurrentRecipe());
        }

        // VoiceCommandHandler는 예외가 발생할 수 있으므로 안전하게 생성
        try {
            voiceCommandHandler = new VoiceCommandHandler(this, this);
        } catch (Exception e) {
            android.util.Log.e("RecipeActivity", "VoiceCommandHandler 초기화 실패", e);
            voiceCommandHandler = null;
        }
        recipeRepository = new RecipeRepository(getApplicationContext());

        // 권한 요청 launcher 초기화
        requestPermissionsLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                permissions -> {
                    // 권한 결과 처리
                    boolean allGranted = true;
                    for (Boolean granted : permissions.values()) {
                        if (!granted) {
                            allGranted = false;
                            break;
                        }
                    }
                    if (!allGranted) {
                        Toast.makeText(this, "일부 권한이 거부되었습니다. 일부 기능이 제한될 수 있습니다.", Toast.LENGTH_SHORT).show();
                    }
                });

        currentStepTitle = findViewById(R.id.current_step_title);
        currentStepIndex = findViewById(R.id.current_step_index);
        currentStepTimer = findViewById(R.id.current_step_timer);
        circleTimer = findViewById(R.id.circle_timer);
        buttonPausePlay = findViewById(R.id.button_pause_play);
        buttonPrevStep = findViewById(R.id.button_prev_step);
        buttonNextStep = findViewById(R.id.button_next_step);

        // 버튼 클릭 리스너 설정 (null 체크)
        if (buttonPausePlay != null) {
            buttonPausePlay.setOnClickListener(v -> toggleTimer());
        }
        if (buttonPrevStep != null) {
            buttonPrevStep.setOnClickListener(v -> navigateToPrevStep());
        }
        if (buttonNextStep != null) {
            buttonNextStep.setOnClickListener(v -> navigateToNextStep());
        }

        setupTimerReceiver();
        askForPermissions();
        checkExactAlarmPermission();

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
                // 초기 UI 설정
                if (circleTimer != null) {
                    circleTimer.setProgress(0);
                }
                if (buttonPausePlay != null) {
                    buttonPausePlay.setText("일시정지");
                    buttonPausePlay.setIconResource(R.drawable.ic_pause);
                }
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
                    int stepIndex = intent.getIntExtra(TimerService.EXTRA_STEP_INDEX, 0);
                    int totalSteps = intent.getIntExtra(TimerService.EXTRA_TOTAL_STEPS, 0);
                    long timeRemainingMs = intent.getLongExtra(TimerService.EXTRA_TIME_REMAINING_MS, 0);
                    long stepDurationMs = intent.getLongExtra(TimerService.EXTRA_STEP_DURATION_MS, 1);

                    // UI 업데이트
                    updateTimerUI(stepDescription, timeRemaining, stepIndex, totalSteps,
                            timeRemainingMs, stepDurationMs);
                } else if (action.equals(TimerService.ACTION_TIMER_STATE_UPDATE)) {
                    boolean isPaused = intent.getBooleanExtra(TimerService.EXTRA_IS_PAUSED, false);
                    updatePausePlayButton(isPaused);
                }
                else if (action.equals(TimerService.ACTION_TIMER_FINISH)) {
                    Toast.makeText(context, "레시피가 종료되었습니다.", Toast.LENGTH_SHORT).show();
                    resetUi();
                }
            }
        };
    }

    private void updatePausePlayButton(boolean isPaused) {
        if (buttonPausePlay != null) {
            if (isPaused) {
                buttonPausePlay.setText("재생");
                buttonPausePlay.setIconResource(R.drawable.ic_play);
            } else {
                buttonPausePlay.setText("일시정지");
                buttonPausePlay.setIconResource(R.drawable.ic_pause);
            }
        }
    }

    /**
     * 타이머 UI를 업데이트합니다.
     */
    private void updateTimerUI(String stepDescription, String timeRemaining, int stepIndex,
                               int totalSteps, long timeRemainingMs, long stepDurationMs) {
        if (currentStepTitle != null) {
            currentStepTitle.setText("[" + (stepIndex + 1) + "단계] " + stepDescription);
        }

        if (currentStepIndex != null) {
            currentStepIndex.setText((stepIndex + 1) + " / " + totalSteps + " 단계");
        }

        if (currentStepTimer != null) {
            currentStepTimer.setText(timeRemaining);
        }

        // 원형 프로그레스 업데이트
        if (circleTimer != null && stepDurationMs > 0) {
            try {
                int progress = (int) ((stepDurationMs - timeRemainingMs) * 100 / stepDurationMs);
                circleTimer.setProgress(Math.max(0, Math.min(100, progress)));
            } catch (Exception e) {
                android.util.Log.e("RecipeActivity", "Progress 업데이트 실패", e);
            }
        }

        // 버튼 상태 업데이트
        if (buttonPrevStep != null) {
            buttonPrevStep.setEnabled(stepIndex > 0);
        }
        if (buttonNextStep != null) {
            buttonNextStep.setEnabled(stepIndex < totalSteps - 1);
        }
    }

    /**
     * 타이머 종료 후 UI를 초기 상태로 리셋합니다.
     */
    private void resetUi() {
        if (currentStepTitle != null) {
            currentStepTitle.setText("");
        }
        if (currentStepIndex != null) {
            currentStepIndex.setText("");
        }
        if (currentStepTimer != null) {
            currentStepTimer.setText("00:00");
        }
        if (circleTimer != null) {
            circleTimer.setProgress(0);
        }
        if (buttonPausePlay != null) {
            buttonPausePlay.setText("시작");
        }
        currentRecipe = null;
    }

    /**
     * 타이머 일시정지/재생 토글
     */
    private void toggleTimer() {
        if (currentRecipe == null) return;
        Intent serviceIntent = new Intent(this, TimerService.class);
        serviceIntent.setAction(TimerService.ACTION_TOGGLE_PAUSE_RESUME);
        serviceIntent.putExtra(TimerService.EXTRA_RECIPE_ID, currentRecipe.getId());
        startService(serviceIntent);
    }

    /**
     * 이전 단계로 이동
     */
    private void navigateToPrevStep() {
        if (currentRecipe == null) return;
        Intent serviceIntent = new Intent(this, TimerService.class);
        serviceIntent.setAction(TimerService.ACTION_PREVIOUS_STEP);
        serviceIntent.putExtra(TimerService.EXTRA_RECIPE_ID, currentRecipe.getId());
        startService(serviceIntent);
    }

    /**
     * 다음 단계로 이동
     */
    private void navigateToNextStep() {
        if (currentRecipe == null) return;
        Intent serviceIntent = new Intent(this, TimerService.class);
        serviceIntent.setAction(TimerService.ACTION_NEXT_STEP);
        serviceIntent.putExtra(TimerService.EXTRA_RECIPE_ID, currentRecipe.getId());
        startService(serviceIntent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 앱이 다시 활성화될 때 권한 상태를 다시 확인
        checkExactAlarmPermission();

        IntentFilter filter = new IntentFilter();
        filter.addAction(TimerService.ACTION_TIMER_UPDATE);
        filter.addAction(TimerService.ACTION_TIMER_FINISH);
        filter.addAction(TimerService.ACTION_TIMER_STATE_UPDATE);
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

    /**
     * Android 12 이상에서 정확한 알람 권한을 확인하고, 권한이 없으면 설정 화면으로 안내합니다.
     */
    private void checkExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                // 권한이 없으면 다이얼로그를 표시하여 설정 화면으로 이동하도록 안내
                new AlertDialog.Builder(this)
                        .setTitle("정확한 알람 권한 필요")
                        .setMessage("타이머가 정확한 시간에 알람을 울리려면 정확한 알람 권한이 필요합니다.\n설정 화면에서 권한을 허용해주세요.")
                        .setPositiveButton("설정으로 이동", (dialog, which) -> {
                            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                            intent.setData(Uri.parse("package:" + getPackageName()));
                            try {
                                startActivity(intent);
                            } catch (Exception e) {
                                // 일부 기기에서는 ACTION_REQUEST_SCHEDULE_EXACT_ALARM이 지원되지 않을 수 있음
                                // 이 경우 앱 설정 화면으로 이동
                                Intent appSettingsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                appSettingsIntent.setData(Uri.parse("package:" + getPackageName()));
                                startActivity(appSettingsIntent);
                                Toast.makeText(this, "앱 설정에서 '정확한 알람 허용'을 켜주세요.", Toast.LENGTH_LONG).show();
                            }
                        })
                        .setNegativeButton("나중에", (dialog, which) -> {
                            Toast.makeText(this, "알람이 정확하지 않을 수 있습니다.", Toast.LENGTH_SHORT).show();
                        })
                        .setCancelable(false)
                        .show();
            }
        }
    }
}