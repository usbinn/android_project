package com.example.recipealarm;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.Objects;

/**
 * AlarmManager로부터 브로드캐스트를 수신하여 알람 로직을 처리하는 클래스입니다.
 * 순차 알람의 핵심적인 역할을 담당합니다.
 */
public class AlarmReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "recipe_alarm_channel";

    @Override
    public void onReceive(Context context, Intent intent) {
        createNotificationChannel(context);

        String recipeName = intent.getStringExtra(RecipeTimer.EXTRA_RECIPE_NAME);
        int stepIndex = intent.getIntExtra(RecipeTimer.EXTRA_STEP_INDEX, -1);

        if (recipeName == null || stepIndex == -1) {
            return;
        }

        // 레시피 찾기
        Recipe recipe = RecipeRepository.getRecipes().stream()
                .filter(r -> Objects.equals(r.getName(), recipeName))
                .findFirst()
                .orElse(null);

        if (recipe == null) {
            return;
        }

        RecipeStep finishedStep = recipe.getSteps().get(stepIndex);

        // 현재 단계가 완료되었음을 알림
        sendNotification(context, "단계 완료!", finishedStep.getDescription(), stepIndex);

        // 다음 단계가 있는지 확인
        int nextStepIndex = stepIndex + 1;
        if (nextStepIndex < recipe.getSteps().size()) {
            // 다음 단계가 있다면, 해당 단계의 알람을 설정
            RecipeTimer.setAlarm(context, recipe, nextStepIndex);
        } else {
            // 마지막 단계였다면, 레시피 완료 알림을 보냄
            sendNotification(context, "요리 완료!", recipe.getName() + " 완성!", 100); // 최종 알림을 위해 다른 ID 사용
        }
    }

    private void sendNotification(Context context, String title, String content, int notificationId) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        notificationManager.notify(notificationId, builder.build());
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "레시피 알람 채널";
            String description = "레시피 타이머 알람을 위한 채널";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
}
