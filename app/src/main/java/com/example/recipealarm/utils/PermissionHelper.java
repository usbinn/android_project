package com.example.recipealarm.utils;

import android.app.AlarmManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

/**
 * 권한 요청 및 확인을 처리하는 유틸리티 클래스
 */
public class PermissionHelper {

    /**
     * 알람 권한을 확인하고, 필요시 설정 화면으로 안내하는 다이얼로그를 표시
     * @param context 액티비티 컨텍스트
     * @param onPermissionGranted 권한이 이미 있을 때 호출될 콜백 (null 가능)
     * @param onPermissionDenied 권한이 거부되었을 때 호출될 콜백 (null 가능)
     */
    public static void checkExactAlarmPermission(Context context,
                                                  Runnable onPermissionGranted,
                                                  Runnable onPermissionDenied) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                showExactAlarmPermissionDialog(context, onPermissionDenied);
            } else if (onPermissionGranted != null) {
                onPermissionGranted.run();
            }
        } else if (onPermissionGranted != null) {
            // Android 12 미만에서는 권한 확인 불필요
            onPermissionGranted.run();
        }
    }

    private static void showExactAlarmPermissionDialog(Context context, Runnable onDenied) {
        new AlertDialog.Builder(context)
                .setTitle("정확한 알람 권한 필요")
                .setMessage("타이머가 정확한 시간에 알람을 울리려면 정확한 알람 권한이 필요합니다.\n설정 화면에서 권한을 허용해주세요.")
                .setPositiveButton("설정으로 이동", (dialog, which) -> {
                    openExactAlarmSettings(context);
                    if (onDenied != null) {
                        onDenied.run();
                    }
                })
                .setNegativeButton("나중에", (dialog, which) -> {
                    Toast.makeText(context, "알람이 정확하지 않을 수 있습니다.", Toast.LENGTH_SHORT).show();
                    if (onDenied != null) {
                        onDenied.run();
                    }
                })
                .setCancelable(false)
                .show();
    }

    private static void openExactAlarmSettings(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setData(Uri.parse("package:" + context.getPackageName()));
            context.startActivity(intent);
        } catch (Exception e) {
            // 일부 기기에서는 ACTION_REQUEST_SCHEDULE_EXACT_ALARM이 지원되지 않을 수 있음
            Intent appSettingsIntent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            appSettingsIntent.setData(Uri.parse("package:" + context.getPackageName()));
            context.startActivity(appSettingsIntent);
            Toast.makeText(context, "앱 설정에서 '정확한 알람 허용'을 켜주세요.", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 필요한 런타임 권한들을 확인하고 반환
     * @param context 컨텍스트
     * @param permissions 확인할 권한 배열
     * @return 모든 권한이 허용되었으면 true
     */
    public static boolean hasPermissions(Context context, String... permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    /**
     * Android 13 이상에서 알림 권한이 필요한지 확인
     */
    public static boolean needsNotificationPermission() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU;
    }

    /**
     * 오디오 녹음 권한이 필요한지 확인
     */
    public static boolean needsAudioPermission() {
        return true; // 모든 Android 버전에서 필요
    }
}

