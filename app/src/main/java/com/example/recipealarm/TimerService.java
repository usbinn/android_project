package com.example.recipealarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

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
public class TimerService extends Service implements RecipeTimer.TimerListener {

    private static final String TAG = "TimerService";
    private static final String CHANNEL_ID = "timer_service_channel";
    private static final int NOTIFICATION_ID = 1;

    // --- Actions for Service Control ---
    public static final String ACTION_START_TIMER = "com.example.recipealarm.ACTION_START_TIMER";
    public static final String ACTION_STOP_TIMER = "com.example.recipealarm.ACTION_STOP_TIMER";
    public static final String ACTION_TOGGLE_PAUSE_RESUME = "com.example.recipealarm.ACTION_TOGGLE_PAUSE_RESUME";
    public static final String ACTION_PREVIOUS_STEP = "com.example.recipealarm.ACTION_PREVIOUS_STEP";
    public static final String ACTION_NEXT_STEP = "com.example.recipealarm.ACTION_NEXT_STEP";


    // --- Actions for Broadcast to UI ---
    public static final String ACTION_TIMER_UPDATE = "com.example.recipealarm.TIMER_UPDATE";
    public static final String ACTION_TIMER_STATE_UPDATE = "com.example.recipealarm.TIMER_STATE_UPDATE";
    public static final String ACTION_TIMER_FINISH = "com.example.recipealarm.TIMER_FINISH";

    // --- Extras for Intent data ---
    public static final String EXTRA_RECIPE_ID = "EXTRA_RECIPE_ID";
    public static final String EXTRA_STEP_DESCRIPTION = "EXTRA_STEP_DESCRIPTION";
    public static final String EXTRA_TIME_REMAINING_FORMATTED = "EXTRA_TIME_REMAINING_FORMATTED";
    public static final String EXTRA_STEP_INDEX = "EXTRA_STEP_INDEX";
    public static final String EXTRA_TOTAL_STEPS = "EXTRA_TOTAL_STEPS";
    public static final String EXTRA_TIME_REMAINING_MS = "EXTRA_TIME_REMAINING_MS";
    public static final String EXTRA_STEP_DURATION_MS = "EXTRA_STEP_DURATION_MS";
    public static final String EXTRA_IS_PAUSED = "EXTRA_IS_PAUSED";


    // 멀티 타이머 관리를 위한 Map. 이제 RecipeTimer 객체를 관리합니다.
    private final Map<String, RecipeTimer> activeTimers = new ConcurrentHashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        String recipeId = intent.getStringExtra(EXTRA_RECIPE_ID);
        if (recipeId == null && action.equals(ACTION_START_TIMER)) {
             String recipeJson = intent.getStringExtra(RecipeTimer.EXTRA_RECIPE_JSON);
             if(recipeJson != null){
                 Recipe recipe = new Gson().fromJson(recipeJson, Recipe.class);
                 if(recipe != null) {
                     recipeId = recipe.getId();
                 }
             }
        }
        
        if (recipeId == null) {
             Log.e(TAG, "Recipe ID가 없는 Intent action: " + action);
             return START_NOT_STICKY;
        }


        Log.d(TAG, "Action: " + action + " for Recipe ID: " + recipeId);

        switch (action) {
            case ACTION_START_TIMER:
                String recipeJson = intent.getStringExtra(RecipeTimer.EXTRA_RECIPE_JSON);
                if (recipeJson != null) {
                    Recipe recipe = new Gson().fromJson(recipeJson, Recipe.class);
                    if (recipe != null) {
                        startRecipeTimer(recipe);
                    }
                }
                break;
            case ACTION_STOP_TIMER:
                stopRecipeTimer(recipeId);
                break;
            case ACTION_TOGGLE_PAUSE_RESUME:
                RecipeTimer timerToToggle = activeTimers.get(recipeId);
                if (timerToToggle != null) {
                    timerToToggle.togglePauseResume();
                }
                break;
            case ACTION_PREVIOUS_STEP:
                RecipeTimer timerToPrev = activeTimers.get(recipeId);
                if (timerToPrev != null) {
                    timerToPrev.previousStep();
                }
                break;
            case ACTION_NEXT_STEP:
                 RecipeTimer timerToNext = activeTimers.get(recipeId);
                if (timerToNext != null) {
                    timerToNext.nextStep();
                }
                break;
        }
        return START_NOT_STICKY;
    }

    private void startRecipeTimer(Recipe recipe) {
        if (activeTimers.containsKey(recipe.getId())) {
            Log.d(TAG, "이미 실행 중인 레시피입니다: " + recipe.getName());
            return;
        }
        Log.d(TAG, "레시피 타이머 시작: " + recipe.getName());
        
        RecipeTimer newTimer = new RecipeTimer(recipe, this, this);
        activeTimers.put(recipe.getId(), newTimer);
        newTimer.start();

        updateForegroundNotification();
    }

    private void stopRecipeTimer(String recipeId) {
        Log.d(TAG, "레시피 타이머 중지 요청: " + recipeId);
        RecipeTimer timer = activeTimers.remove(recipeId);
        if (timer != null) {
            timer.stop();
        }

        if (activeTimers.isEmpty()) {
            Log.d(TAG, "모든 타이머가 종료되어 서비스를 중지합니다.");
            stopSelf();
        } else {
            updateForegroundNotification();
        }
    }

    private void updateForegroundNotification() {
        int timerCount = activeTimers.size();
        if (timerCount == 0) {
            stopForeground(true);
            return;
        }
        
        String title;
        String text;

        if (timerCount == 1) {
            RecipeTimer timer = activeTimers.values().iterator().next();
            Recipe recipe = timer.getRecipe();
            RecipeStep step = recipe.getSteps().get(timer.getCurrentStepIndex());
            title = "진행 중: " + step.getDescription();
            text = recipe.getName();
        } else {
            title = timerCount + "개의 레시피가 진행 중입니다.";
            text = activeTimers.values().stream()
                    .map(t -> t.getRecipe().getName())
                    .collect(java.util.stream.Collectors.toList()).toString();
        }
        startForeground(NOTIFICATION_ID, createNotification(title, text));
    }

    private Notification createNotification(String title, String text) {
        // 알림 클릭 시 MainActivity를 열도록 Intent 설정
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent) // PendingIntent 설정
                .setOnlyAlertOnce(true)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        activeTimers.values().forEach(RecipeTimer::stop);
        activeTimers.clear();
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

    // --- RecipeTimer.TimerListener Implementation ---

    @Override
    public void onUpdate(String recipeId, String stepDescription, String timeRemaining, int stepIndex, int totalSteps, long timeRemainingMs, long stepDurationMs) {
        Intent intent = new Intent(ACTION_TIMER_UPDATE);
        intent.putExtra(EXTRA_RECIPE_ID, recipeId);
        intent.putExtra(EXTRA_STEP_DESCRIPTION, stepDescription);
        intent.putExtra(EXTRA_TIME_REMAINING_FORMATTED, timeRemaining);
        intent.putExtra(EXTRA_STEP_INDEX, stepIndex);
        intent.putExtra(EXTRA_TOTAL_STEPS, totalSteps);
        intent.putExtra(EXTRA_TIME_REMAINING_MS, timeRemainingMs);
        intent.putExtra(EXTRA_STEP_DURATION_MS, stepDurationMs);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);

        // 포그라운드 알림도 업데이트 (선택적)
        updateForegroundNotification();
    }

    @Override
    public void onStateChanged(String recipeId, boolean isPaused) {
        Intent intent = new Intent(ACTION_TIMER_STATE_UPDATE);
        intent.putExtra(EXTRA_RECIPE_ID, recipeId);
        intent.putExtra(EXTRA_IS_PAUSED, isPaused);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        updateForegroundNotification();
    }
    
    @Override
    public void onStepChanged(String recipeId, int newStepIndex) {
        // TTS로 새로운 단계 알려주기 등 추가 로직 구현 가능
        updateForegroundNotification();
    }

    @Override
    public void onFinish(String recipeId) {
        Intent intent = new Intent(ACTION_TIMER_FINISH);
        intent.putExtra(EXTRA_RECIPE_ID, recipeId);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
        stopRecipeTimer(recipeId);
    }
}

