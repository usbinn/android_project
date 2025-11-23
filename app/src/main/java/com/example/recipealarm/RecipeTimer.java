package com.example.recipealarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.CountDownTimer;
import android.util.Log;
import com.google.gson.Gson;
import java.util.Locale;

/**
 * 개별 레시피 타이머의 모든 상태와 로직을 관리하는 클래스입니다.
 * 이 클래스는 일시정지, 재개, 단계 이동 기능을 포함한 타이머의 전체 생명주기를 책임집니다.
 * TimerService는 이 클래스의 인스턴스를 생성하여 각 레시피 타이머를 관리합니다.
 *
 * 주요 기능:
 * - CountDownTimer를 이용한 정밀한 포그라운드 타이머
 * - 남은 시간을 저장하여 일시정지/재개 기능 구현
 * - 다음/이전 단계로 자유롭게 이동
 * - TimerListener를 통해 TimerService로 상태 변경(UI 업데이트용)을 알림
 * - AlarmManager를 이용한 백업 알람 설정/취소 (정적 메소드로 유지)
 */
public class RecipeTimer {

    private static final String TAG = "RecipeTimer";
    public static final String EXTRA_RECIPE_JSON = "com.example.recipealarm.RECIPE_JSON";
    public static final String EXTRA_STEP_INDEX = "com.example.recipealarm.STEP_INDEX";

    // --- Listener Interface ---
    public interface TimerListener {
        void onUpdate(String recipeId, String stepDescription, String timeRemaining, int stepIndex, int totalSteps, long timeRemainingMs, long stepDurationMs);
        void onStateChanged(String recipeId, boolean isPaused);
        void onStepChanged(String recipeId, int newStepIndex);
        void onFinish(String recipeId);
    }

    // --- Instance Variables ---
    private final Recipe recipe;
    private final TimerListener listener;
    private final Context context;

    private int currentStepIndex = 0;
    private long timeRemainingInStepMs = 0;
    private boolean isPaused = true;
    private CountDownTimer countDownTimer;

    // --- Constructor ---
    public RecipeTimer(Recipe recipe, Context context, TimerListener listener) {
        this.recipe = recipe;
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    // --- Public Control Methods ---

    /** 타이머를 0단계부터 시작합니다. */
    public void start() {
        Log.d(TAG, "Starting recipe: " + recipe.getName());
        this.isPaused = false;
        startStep(0);
    }

    /** 타이머를 완전히 중지하고 모든 알람을 취소합니다. */
    public void stop() {
        Log.d(TAG, "Stopping recipe: " + recipe.getName());
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        cancelAlarms(context, recipe);
        listener.onFinish(recipe.getId());
    }

    /** 타이머를 일시정지하거나 재개합니다. */
    public void togglePauseResume() {
        this.isPaused = !this.isPaused;
        if (isPaused) {
            Log.d(TAG, "Pausing timer for " + recipe.getName());
            if (countDownTimer != null) {
                countDownTimer.cancel();
            }
        } else {
            Log.d(TAG, "Resuming timer for " + recipe.getName());
            createCountDownTimer(timeRemainingInStepMs);
        }
        listener.onStateChanged(recipe.getId(), this.isPaused);
    }

    /** 다음 단계로 이동합니다. */
    public void nextStep() {
        if (currentStepIndex < recipe.getSteps().size() - 1) {
            Log.d(TAG, "Moving to next step for " + recipe.getName());
            startStep(currentStepIndex + 1);
        }
    }

    /** 이전 단계로 이동합니다. */
    public void previousStep() {
        if (currentStepIndex > 0) {
            Log.d(TAG, "Moving to previous step for " + recipe.getName());
            startStep(currentStepIndex - 1);
        }
    }

    // --- Private Helper Methods ---

    /** 특정 단계를 시작합니다. */
    private void startStep(int stepIndex) {
        if (stepIndex < 0 || stepIndex >= recipe.getSteps().size()) {
            stop(); // 모든 단계가 끝나면 타이머 종료
            return;
        }

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        this.currentStepIndex = stepIndex;
        this.timeRemainingInStepMs = recipe.getSteps().get(stepIndex).getDurationInSeconds() * 1000L;

        // 백업 알람 설정
        setAlarm(context, recipe, stepIndex);

        // UI에 단계 변경 알림
        listener.onStepChanged(recipe.getId(), currentStepIndex);
        this.isPaused = false;
        listener.onStateChanged(recipe.getId(), this.isPaused);


        createCountDownTimer(this.timeRemainingInStepMs);
    }

    /** 지정된 시간으로 CountDownTimer를 생성하고 시작합니다. */
    private void createCountDownTimer(long durationMs) {
        final RecipeStep step = recipe.getSteps().get(currentStepIndex);
        final long stepDuration = step.getDurationInSeconds() * 1000L;

        // 즉시 UI 업데이트를 위해 첫 onUpdate 호출
        listener.onUpdate(recipe.getId(), step.getDescription(), formatTime(durationMs), currentStepIndex, recipe.getSteps().size(), durationMs, stepDuration);

        countDownTimer = new CountDownTimer(durationMs, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                timeRemainingInStepMs = millisUntilFinished;
                listener.onUpdate(recipe.getId(), step.getDescription(), formatTime(millisUntilFinished), currentStepIndex, recipe.getSteps().size(), millisUntilFinished, stepDuration);
            }

            @Override
            public void onFinish() {
                timeRemainingInStepMs = 0;
                nextStep(); // 현재 단계가 끝나면 자동으로 다음 단계로 이동
            }
        }.start();
    }

    /** 밀리초를 "mm:ss" 형식의 문자열로 변환합니다. */
    private String formatTime(long millis) {
        long minutes = (millis / 1000) / 60;
        long seconds = (millis / 1000) % 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }
    
    public Recipe getRecipe() {
        return recipe;
    }

    public int getCurrentStepIndex() {
        return currentStepIndex;
    }

    // --- Static AlarmManager Methods (Unchanged) ---

    public static void setAlarm(Context context, Recipe recipe, int stepIndex) {
        if (recipe == null || stepIndex < 0 || stepIndex >= recipe.getSteps().size()) {
            return;
        }

        RecipeStep step = recipe.getSteps().get(stepIndex);
        long durationInMillis = step.getDurationInSeconds() * 1000L;

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);

        Gson gson = new Gson();
        intent.putExtra(EXTRA_RECIPE_JSON, gson.toJson(recipe));
        intent.putExtra(EXTRA_STEP_INDEX, stepIndex);

        int requestCode = recipe.getId().hashCode() + stepIndex;

        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long alarmTime = System.currentTimeMillis() + durationInMillis;

        try {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "알람 설정 실패 (권한 없음): " + e.getMessage());
        }
    }

    public static void cancelAlarms(Context context, Recipe recipe) {
        if (recipe == null) return;
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        for (int i = 0; i < recipe.getSteps().size(); i++) {
            int requestCode = recipe.getId().hashCode() + i;
            Intent intent = new Intent(context, AlarmReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);

            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent);
                pendingIntent.cancel();
            }
        }
    }
}