package com.example.recipealarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.example.recipealarm.utils.Constants;
import com.google.gson.Gson;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 레시피 타이머를 백그라운드에서 안정적으로 실행하기 위한 Foreground Service 입니다.
 * 이 서비스는 이제 여러 레시피의 타이머를 동시에 관리할 수 있습니다.
 *
 * 각 타이머는 레시피 ID를 키로 하는 Map을 통해 관리됩니다.
 * 서비스는 활성 타이머가 하나라도 있는 동안 Foreground 상태를 유지하며,
 * 모든 타이머가 종료되면 스스로 중지됩니다.
 */
public class TimerService extends Service {

    private static final String TAG = "TimerService";
    private static final String CHANNEL_ID = "timer_service_channel";
    private static final int NOTIFICATION_ID = 1;

    // Extras for communication (Actions are in Constants)
    public static final String EXTRA_RECIPE_ID = Constants.EXTRA_RECIPE_ID;

    // 멀티 타이머 관리를 위한 Map
    private final Map<String, CountDownTimer> activeTimers = new ConcurrentHashMap<>();
    private final Map<String, Recipe> activeRecipes = new ConcurrentHashMap<>();
    private final Map<String, Integer> activeSteps = new ConcurrentHashMap<>();
    private final Map<String, Boolean> pausedStates = new ConcurrentHashMap<>();
    private final Map<String, Long> pausedTimeRemaining = new ConcurrentHashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            String recipeId = intent.getStringExtra(EXTRA_RECIPE_ID);
            
            if (Constants.ACTION_START_TIMER.equals(action)) {
                String recipeJson = intent.getStringExtra(RecipeTimer.EXTRA_RECIPE_JSON);
                if (recipeJson != null) {
                    Recipe recipe = new Gson().fromJson(recipeJson, Recipe.class);
                    if (recipe != null) {
                        startRecipeTimer(recipe);
                    }
                }
            } else if (Constants.ACTION_STOP_TIMER.equals(action)) {
                if (recipeId != null) {
                    stopRecipeTimer(recipeId);
                }
            } else if (Constants.ACTION_PAUSE_TIMER.equals(action)) {
                if (recipeId != null) {
                    pauseRecipeTimer(recipeId);
                }
            } else if (Constants.ACTION_RESUME_TIMER.equals(action)) {
                if (recipeId != null) {
                    resumeRecipeTimer(recipeId);
                }
            } else if (Constants.ACTION_NAVIGATE_STEP.equals(action)) {
                if (recipeId != null) {
                    String direction = intent.getStringExtra(Constants.EXTRA_NAVIGATE_DIRECTION);
                    navigateStep(recipeId, direction);
                }
            }
        }
        return START_NOT_STICKY;
    }

    private void startRecipeTimer(Recipe recipe) {
        if (activeRecipes.containsKey(recipe.getId())) {
            Log.d(TAG, "이미 실행 중인 레시피입니다: " + recipe.getName());
            return;
        }
        Log.d(TAG, "레시피 타이머 시작: " + recipe.getName());
        activeRecipes.put(recipe.getId(), recipe);
        pausedStates.put(recipe.getId(), false);

        // 백그라운드 알람 시퀀스 시작
        RecipeTimer.setAlarm(this, recipe, 0);
        // 포그라운드 타이머 시작
        startStep(recipe, 0, recipe.getSteps().get(0).getDurationInSeconds() * 1000L);
    }

    private void stopRecipeTimer(String recipeId) {
        Log.d(TAG, "레시피 타이머 중지: " + recipeId);
        Recipe recipe = activeRecipes.get(recipeId);
        if (recipe != null) {
            RecipeTimer.cancelAlarms(this, recipe);
        }

        CountDownTimer timer = activeTimers.get(recipeId);
        if (timer != null) {
            timer.cancel();
        }

        activeTimers.remove(recipeId);
        activeRecipes.remove(recipeId);
        activeSteps.remove(recipeId);
        pausedStates.remove(recipeId);
        pausedTimeRemaining.remove(recipeId);

        if (activeRecipes.isEmpty()) {
            Log.d(TAG, "모든 타이머가 종료되어 서비스를 중지합니다.");
            stopSelf();
        } else {
            updateForegroundNotification();
        }
    }

    private void pauseRecipeTimer(String recipeId) {
        if (!activeRecipes.containsKey(recipeId)) {
            return;
        }

        CountDownTimer timer = activeTimers.get(recipeId);
        if (timer != null) {
            timer.cancel();
        }
        activeTimers.remove(recipeId);
        
        pausedStates.put(recipeId, true);
        
        // 일시정지 상태 브로드캐스트
        Recipe recipe = activeRecipes.get(recipeId);
        int stepIndex = activeSteps.getOrDefault(recipeId, 0);
        RecipeStep step = recipe.getSteps().get(stepIndex);
        long remainingMs = pausedTimeRemaining.getOrDefault(recipeId, step.getDurationInSeconds() * 1000L);
        long durationMs = step.getDurationInSeconds() * 1000L;
        
        broadcastUpdate(recipeId, step.getDescription(), formatTime(remainingMs), 
                stepIndex, recipe.getSteps().size(), remainingMs, durationMs, true);
        updateForegroundNotification();
        
        Log.d(TAG, "레시피 타이머 일시정지: " + recipeId);
    }

    private void resumeRecipeTimer(String recipeId) {
        if (!activeRecipes.containsKey(recipeId) || !pausedStates.getOrDefault(recipeId, false)) {
            return;
        }

        Recipe recipe = activeRecipes.get(recipeId);
        int stepIndex = activeSteps.getOrDefault(recipeId, 0);
        long remainingMs = pausedTimeRemaining.getOrDefault(recipeId, recipe.getSteps().get(stepIndex).getDurationInSeconds() * 1000L);
        
        pausedStates.put(recipeId, false);
        startStep(recipe, stepIndex, remainingMs);
        
        Log.d(TAG, "레시피 타이머 재개: " + recipeId);
    }

    private void navigateStep(String recipeId, String direction) {
        Recipe recipe = activeRecipes.get(recipeId);
        if (recipe == null) {
            return;
        }

        int currentStep = activeSteps.getOrDefault(recipeId, 0);
        int newStep;
        
        if ("prev".equals(direction)) {
            newStep = currentStep - 1;
            if (newStep < 0) {
                return; // 첫 번째 단계입니다
            }
        } else if ("next".equals(direction)) {
            newStep = currentStep + 1;
            if (newStep >= recipe.getSteps().size()) {
                return; // 마지막 단계입니다
            }
        } else {
            return;
        }

        // 현재 타이머 취소
        CountDownTimer timer = activeTimers.get(recipeId);
        if (timer != null) {
            timer.cancel();
        }
        activeTimers.remove(recipeId);

        // 새로운 단계 시작
        RecipeTimer.cancelAlarms(this, recipe);
        RecipeTimer.setAlarm(this, recipe, newStep);
        startStep(recipe, newStep, recipe.getSteps().get(newStep).getDurationInSeconds() * 1000L);
        
        Log.d(TAG, "단계 이동: " + recipeId + " -> " + newStep);
    }

    private void startStep(Recipe recipe, int stepIndex, long remainingMs) {
        String recipeId = recipe.getId();
        if (stepIndex < 0 || stepIndex >= recipe.getSteps().size()) {
            Log.d(TAG, "레시피 종료: " + recipe.getName());
            broadcastFinish(recipeId);
            stopRecipeTimer(recipeId);
            return;
        }

        activeSteps.put(recipeId, stepIndex);
        RecipeStep step = recipe.getSteps().get(stepIndex);

        updateForegroundNotification();

        // 기존 타이머가 있으면 취소
        CountDownTimer oldTimer = activeTimers.get(recipeId);
        if (oldTimer != null) {
            oldTimer.cancel();
        }

        long stepDurationMs = step.getDurationInSeconds() * 1000L;
        long startTime = Math.min(remainingMs, stepDurationMs);
        
        // 초기 상태 브로드캐스트 (타이머 시작 전)
        String initialTimeFormatted = formatTime(startTime);
        broadcastUpdate(recipeId, step.getDescription(), initialTimeFormatted, 
                stepIndex, recipe.getSteps().size(), startTime, stepDurationMs, false);
        
        CountDownTimer newTimer = new CountDownTimer(startTime, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                String timeFormatted = formatTime(millisUntilFinished);
                pausedTimeRemaining.put(recipeId, millisUntilFinished);
                // 단계 정보와 진행률을 포함하여 브로드캐스트
                broadcastUpdate(recipeId, step.getDescription(), timeFormatted, 
                        stepIndex, recipe.getSteps().size(), millisUntilFinished, stepDurationMs, false);
                updateForegroundNotification();
            }

            @Override
            public void onFinish() {
                pausedTimeRemaining.remove(recipeId);
                // 다음 단계로 이동 (startStep 내부에서 범위 체크를 수행)
                int nextStepIndex = stepIndex + 1;
                if (nextStepIndex < recipe.getSteps().size()) {
                    long nextStepDurationMs = recipe.getSteps().get(nextStepIndex).getDurationInSeconds() * 1000L;
                    startStep(recipe, nextStepIndex, nextStepDurationMs);
                } else {
                    // 마지막 단계 완료 - startStep이 종료 처리를 합니다
                    startStep(recipe, nextStepIndex, 0);
                }
            }
        };
        newTimer.start();
        activeTimers.put(recipeId, newTimer);
    }

    private void updateForegroundNotification() {
        String title;
        String text;
        int timerCount = activeRecipes.size();

        if (timerCount == 0) {
            return;
        } else if (timerCount == 1) {
            Recipe recipe = activeRecipes.values().iterator().next();
            int stepIndex = activeSteps.getOrDefault(recipe.getId(), 0);
            RecipeStep step = recipe.getSteps().get(stepIndex);
            boolean isPaused = pausedStates.getOrDefault(recipe.getId(), false);
            title = (isPaused ? "[일시정지] " : "") + "진행 중: " + step.getDescription();
            text = recipe.getName();
        } else {
            title = timerCount + "개의 레시피가 진행 중입니다.";
            text = String.join(", ", activeRecipes.values().stream().map(Recipe::getName).collect(java.util.stream.Collectors.toList()));
        }
        startForeground(NOTIFICATION_ID, createNotification(title, text));
    }

    private Notification createNotification(String title, String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void broadcastUpdate(String recipeId, String stepDescription, String timeRemaining, 
                                 int stepIndex, int totalSteps, long timeRemainingMs, long stepDurationMs, boolean isPaused) {
        Intent intent = new Intent(Constants.ACTION_TIMER_UPDATE);
        intent.putExtra(EXTRA_RECIPE_ID, recipeId);
        intent.putExtra(Constants.EXTRA_STEP_DESCRIPTION, stepDescription);
        intent.putExtra(Constants.EXTRA_TIME_REMAINING_FORMATTED, timeRemaining);
        intent.putExtra(Constants.EXTRA_STEP_INDEX, stepIndex);
        intent.putExtra(Constants.EXTRA_TOTAL_STEPS, totalSteps);
        intent.putExtra(Constants.EXTRA_TIME_REMAINING_MS, timeRemainingMs);
        intent.putExtra(Constants.EXTRA_STEP_DURATION_MS, stepDurationMs);
        intent.putExtra(Constants.EXTRA_IS_PAUSED, isPaused);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void broadcastFinish(String recipeId) {
        Intent intent = new Intent(Constants.ACTION_TIMER_FINISH);
        intent.putExtra(EXTRA_RECIPE_ID, recipeId);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private String formatTime(long millis) {
        long minutes = (millis / 1000) / 60;
        long seconds = (millis / 1000) % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        activeTimers.values().forEach(CountDownTimer::cancel);
        activeTimers.clear();
        activeRecipes.clear();
        activeSteps.clear();
        pausedStates.clear();
        pausedTimeRemaining.clear();
        Log.d(TAG, "TimerService 소멸");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "타이머 서비스 채널",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(serviceChannel);
        }
    }
}
