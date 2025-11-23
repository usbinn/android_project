package com.example.recipealarm;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import com.google.gson.Gson;

/**
 * AlarmManager를 사용하여 백그라운드에서 안전하게 동작하는 알람을 예약하는 클래스입니다.
 * 이 클래스는 레시피의 알람 시퀀스를 설정하고 취소하는 역할을 담당합니다.
 * 알람은 앱이 백그라운드에 있거나 종료된 상태에서도 울립니다.
 */
public class RecipeTimer {

    public static final String EXTRA_RECIPE_JSON = "com.example.recipealarm.RECIPE_JSON";
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
            return; // 잘못된 입력
        }

        RecipeStep step = recipe.getSteps().get(stepIndex);
        long durationInMillis = step.getDurationInSeconds() * 1000L;

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, AlarmReceiver.class);

        // AlarmReceiver가 레시피 정보를 다시 조회할 필요 없도록, 객체를 JSON 문자열로 변환하여 전달합니다.
        Gson gson = new Gson();
        intent.putExtra(EXTRA_RECIPE_JSON, gson.toJson(recipe));
        intent.putExtra(EXTRA_STEP_INDEX, stepIndex);

        // 멀티 타이머를 지원하고 각 알람을 고유하게 식별하기 위해,
        // 레시피의 고유 ID와 단계 인덱스를 조합하여 request code를 생성합니다.
        int requestCode = recipe.getId().hashCode() + stepIndex;

        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        long alarmTime = System.currentTimeMillis() + durationInMillis;

        // 정확한 시간에 알람이 울리도록 버전에 따라 적절한 메서드 사용
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                // Android 6.0 이상에서는 setExactAndAllowWhileIdle 사용 (Android 12 이상 권장)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    // Android 12 이상: setExactAndAllowWhileIdle 사용
                    // 권한이 없으면 SecurityException 발생 가능
                    if (alarmManager.canScheduleExactAlarms()) {
                        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent);
                        Log.d("RecipeTimer", "알람 설정 성공 (setExactAndAllowWhileIdle): " + recipe.getName() + " 단계 " + stepIndex);
                    } else {
                        // 권한이 없으면 setExact로 대체 (덜 정확하지만 작동함)
                        Log.w("RecipeTimer", "정확한 알람 권한이 없어 setExact로 대체합니다.");
                        alarmManager.setExact(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent);
                    }
                } else {
                    // Android 6.0 ~ 11
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent);
                    Log.d("RecipeTimer", "알람 설정 성공 (setExact): " + recipe.getName() + " 단계 " + stepIndex);
                }
            } else {
                // Android 6.0 미만 (레거시 지원)
                alarmManager.set(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent);
                Log.d("RecipeTimer", "알람 설정 성공 (set): " + recipe.getName() + " 단계 " + stepIndex);
            }
        } catch (SecurityException e) {
            Log.e("RecipeTimer", "알람 설정 실패 (권한 없음): " + e.getMessage());
            // 권한이 없으면 일반 알람으로 대체 시도
            try {
                alarmManager.set(AlarmManager.RTC_WAKEUP, alarmTime, pendingIntent);
                Log.w("RecipeTimer", "일반 알람으로 대체 설정했습니다.");
            } catch (Exception e2) {
                Log.e("RecipeTimer", "알람 설정 완전 실패: " + e2.getMessage());
            }
        } catch (Exception e) {
            Log.e("RecipeTimer", "알람 설정 중 오류 발생: " + e.getMessage(), e);
        }
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
            // 알람을 설정할 때와 동일한 방법으로 request code를 생성하여 정확한 PendingIntent를 찾습니다.
            int requestCode = recipe.getId().hashCode() + i;
            Intent intent = new Intent(context, AlarmReceiver.class);
            PendingIntent pendingIntent = PendingIntent.getBroadcast(context, requestCode, intent,
                    PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);

            if (pendingIntent != null) {
                alarmManager.cancel(pendingIntent);
            }
        }
    }
}
