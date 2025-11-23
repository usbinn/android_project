package com.example.recipealarm;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import com.google.gson.Gson;

/**
 * AlarmManager로부터 브로드캐스트를 수신하여 알람 로직을 처리하는 클래스입니다.
 * 순차 알람의 핵심적인 역할을 담당하며, 이제 음성 안내(TTS)와 진동 기능도 처리합니다.
 */
public class AlarmReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "recipe_alarm_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        createNotificationChannel(context);

        String recipeJson = intent.getStringExtra(RecipeTimer.EXTRA_RECIPE_JSON);
        int stepIndex = intent.getIntExtra(RecipeTimer.EXTRA_STEP_INDEX, -1);

        if (recipeJson == null || stepIndex == -1) {
            return;
        }

        Gson gson = new Gson();
        Recipe recipe = gson.fromJson(recipeJson, Recipe.class);

        if (recipe == null) {
            return;
        }

        RecipeStep finishedStep = recipe.getSteps().get(stepIndex);
        int notificationId = recipe.getId().hashCode() + stepIndex;

        // 현재 단계가 완료되었음을 알림
        sendNotification(context, "단계 완료: " + finishedStep.getDescription(), "다음 단계를 준비하세요.", notificationId);

        // 다음 단계가 있는지 확인
        int nextStepIndex = stepIndex + 1;
        if (nextStepIndex < recipe.getSteps().size()) {
            RecipeStep nextStep = recipe.getSteps().get(nextStepIndex);
            // 다음 단계 알람 설정
            RecipeTimer.setAlarm(context, recipe, nextStepIndex);
            // TTS로 다음 단계 안내
            new TTSHandler(context, "다음 단계는, " + nextStep.getDescription() + " 입니다.");
        } else {
            // 마지막 단계였다면, 레시피 완료 알림 및 음성 안내
            int finalNotificationId = recipe.getId().hashCode() + 1000;
            String completionMessage = recipe.getName() + " 완성!";
            sendNotification(context, "요리 완료!", completionMessage, finalNotificationId);
            new TTSHandler(context, "요리가 완성되었습니다. 맛있게 드세요!");
        }
    }

    /**
     * 사용자에게 알림을 보냅니다. 이제 진동 기능이 포함됩니다.
     * @param context 컨텍스트
     * @param title 알림 제목
     * @param content 알림 내용
     * @param notificationId 알림 ID
     */
    private void sendNotification(Context context, String title, String content, int notificationId) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                // 진동 패턴 설정: 0.5초 대기 -> 0.5초 진동 -> 0.25초 대기 -> 0.5초 진동
                .setVibrate(new long[]{500, 500, 250, 500});

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(notificationId, builder.build());
    }

    /**
     * 알림 채널을 생성합니다. 채널에 진동을 활성화하는 설정을 추가합니다.
     * @param context 컨텍스트
     */
    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "레시피 알람 채널";
            String description = "레시피 타이머 알람을 위한 채널";
            int importance = NotificationManager.IMPORTANCE_HIGH; // 높은 중요도로 설정해야 헤드업 알림이 뜹니다.
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // 채널에 진동 활성화
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{500, 500, 250, 500});

            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}
