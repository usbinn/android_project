package com.example.recipealarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/**
 * AlarmManager를 사용하여 백그라운드에서 안전하게 동작하는 알람을 예약하는 클래스입니다.
 * 이 클래스는 레시피의 알람 시퀀스를 설정하고 취소하는 역할을 담당합니다.
 * 알람은 앱이 백그라운드에 있거나 종료된 상태에서도 울립니다.
 */
public class RecipeTimer {

    public static final String EXTRA_RECIPE_NAME = "com.example.recipealarm.RECIPE_NAME";
    public static final String EXTRA_STEP_INDEX = "com.example.recipealarm.STEP_INDEX";

    /**
     * 레시피의 특정 단계에 대한 백그라운드 알람을 설정합니다.
     * 이 알람이 울리면 AlarmReceiver가 실행되고, 이어서 다음 단계의 알람을 설정합니다.
     *
     * 레시피를 시작하려면 이 메소드를 stepIndex = 0으로 호출하면 됩니다.
     *
     * @param context 애플리케이션 컨텍스트.
     * @param recipe 알람을 설정할 레시피.
     * @param stepIndex 알람을 설정할 단계의 인덱스.
     */
    public static void setAlarm(Context context, Recipe recipe, int stepIndex) {
        if (recipe == null || stepIndex < 0 || stepIndex >= recipe.getSteps().size()) {
            return; // Invalid input
        }

        RecipeStep step = recipe.getSteps().get(stepIndex);
        long durationInMillis = step.getDurationInSeconds() * 1000L;

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);
        intent.putExtra(EXTRA_RECIPE_NAME, recipe.getName());
        intent.putExtra(EXTRA_STEP_INDEX, stepIndex);

        // 각 PendingIntent를 고유하게 만들기 위해 stepIndex를 request code로 사용합니다.
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, stepIndex, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long alarmTime = System.currentTimeMillis() + durationInMillis;

        alarmManager.set(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent);
    }

    /**
     * 특정 레시피에 대해 예약된 모든 백그라운드 알람을 취소합니다.
     * 사용자가 수동으로 레시피를 중단할 때 호출해야 합니다.
     *
     * @param context 애플리케이션 컨텍스트.
     * @param recipe 알람을 취소할 레시피.
     */
    public static void cancelAlarms(Context context, Recipe recipe) {
        if (recipe == null) return;
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        for (int i = 0; i < recipe.getSteps().size(); i++) {
            Intent intent = new Intent(context, AlarmReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, i, intent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent);
            }
        }
    }
}
