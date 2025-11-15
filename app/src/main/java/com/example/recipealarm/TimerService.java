package com.example.recipealarm;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
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
public class TimerService extends Service {

    private static final String TAG = "TimerService";
    private static final String CHANNEL_ID = "timer_service_channel";
    private static final int NOTIFICATION_ID = 1;

    // Actions and Extras for communication
    public static final String ACTION_TIMER_UPDATE = "com.example.recipealarm.TIMER_UPDATE";
    public static final String EXTRA_RECIPE_ID = "EXTRA_RECIPE_ID";
    public static final String EXTRA_STEP_DESCRIPTION = "EXTRA_STEP_DESCRIPTION";
    public static final String EXTRA_TIME_REMAINING_FORMATTED = "EXTRA_TIME_REMAINING_FORMATTED";
    public static final String ACTION_TIMER_FINISH = "com.example.recipealarm.TIMER_FINISH";

    public static final String ACTION_START_TIMER = "com.example.recipealarm.ACTION_START_TIMER";
    public static final String ACTION_STOP_TIMER = "com.example.recipealarm.ACTION_STOP_TIMER";

    // 멀티 타이머 관리를 위한 Map
    private final Map<String, CountDownTimer> activeTimers = new ConcurrentHashMap<>();
    private final Map<String, Recipe> activeRecipes = new ConcurrentHashMap<>();
    private final Map<String, Integer> activeSteps = new ConcurrentHashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getAction() != null) {
            String action = intent.getAction();
            if (action.equals(ACTION_START_TIMER)) {
                String recipeJson = intent.getStringExtra(RecipeTimer.EXTRA_RECIPE_JSON);
                if (recipeJson != null) {
                    Recipe recipe = new Gson().fromJson(recipeJson, Recipe.class);
                    if (recipe != null) {
                        startRecipeTimer(recipe);
                    }
                }
            } else if (action.equals(ACTION_STOP_TIMER)) {
                String recipeId = intent.getStringExtra(EXTRA_RECIPE_ID);
                if (recipeId != null) {
                    stopRecipeTimer(recipeId);
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

        // 백그라운드 알람 시퀀스 시작
        RecipeTimer.setAlarm(this, recipe, 0);
        // 포그라운드 타이머 시작
        startStep(recipe, 0);
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

        if (activeRecipes.isEmpty()) {
            Log.d(TAG, "모든 타이머가 종료되어 서비스를 중지합니다.");
            stopSelf();
        } else {
            updateForegroundNotification();
        }
    }

    private void startStep(Recipe recipe, int stepIndex) {
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

        CountDownTimer newTimer = new CountDownTimer(step.getDurationInSeconds() * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                String timeFormatted = formatTime(millisUntilFinished);
                // 테스트용 UI는 타이머 하나만 보여주므로, 마지막으로 업데이트된 타이머 정보만 전송
                broadcastUpdate(recipeId, step.getDescription(), timeFormatted);
                updateForegroundNotification();
            }

            @Override
            public void onFinish() {
                // AlarmReceiver가 다음 단계를 호출하지만, 앱이 켜져있을 때의 즉각적인 반응을 위해 여기서도 호출
                startStep(recipe, stepIndex + 1);
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
            title = "진행 중: " + step.getDescription();
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

    private void broadcastUpdate(String recipeId, String stepDescription, String timeRemaining) {
        Intent intent = new Intent(ACTION_TIMER_UPDATE);
        intent.putExtra(EXTRA_RECIPE_ID, recipeId);
        intent.putExtra(EXTRA_STEP_DESCRIPTION, stepDescription);
        intent.putExtra(EXTRA_TIME_REMAINING_FORMATTED, timeRemaining);
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void broadcastFinish(String recipeId) {
        Intent intent = new Intent(ACTION_TIMER_FINISH);
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
