package com.example.recipealarm;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.util.Locale;

/**
 * 이 액티비티는 테스트 및 예제용 구현체입니다.
 * 핵심 로직 클래스들의 사용법을 보여줍니다:
 * - RecipeRepository: 레시피 데이터를 가져옵니다.
 * - RecipeTimer: 백그라운드에서 안전하게 동작하는 알람 시퀀스를 시작하고 중지합니다.
 * - VoiceCommandHandler: 음성 명령을 시작합니다.
 * - CountDownTimer: 포그라운드에서 실시간 카운트다운을 표시합니다.
 *
 * UI 개발자는 이 액티비티를 참고하여, 백엔드 로직을 새로운 UI에 연결하는 방법을 파악하고
 * 이 액티비티를 새 것으로 교체할 수 있습니다.
 */
public class RecipeActivity extends AppCompatActivity implements VoiceCommandHandler.VoiceCommandCallback {

    private VoiceCommandHandler voiceCommandHandler;
    private TextView stepTextView;
    private TextView timerTextView;

    private CountDownTimer countDownTimer;
    private Recipe currentRecipe;
    private int currentStepIndex = -1;

    // 여러 권한을 요청하기 위한 런처
    private final ActivityResultLauncher<String[]> requestPermissionsLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), permissions -> {
                permissions.forEach((permission, isGranted) -> {
                    if (!isGranted) {
                        if (permission.equals(Manifest.permission.POST_NOTIFICATIONS)) {
                            Toast.makeText(this, "알람을 위해 알림 권한이 필요합니다.", Toast.LENGTH_LONG).show();
                        } else if (permission.equals(Manifest.permission.RECORD_AUDIO)) {
                            Toast.makeText(this, "음성 제어를 위해 마이크 권한이 필요합니다.", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recipe);

        // --- 핵심 로직 초기화 ---
        // 음성 명령 핸들러 초기화
        voiceCommandHandler = new VoiceCommandHandler(this, this);

        // --- UI 설정 ---
        stepTextView = findViewById(R.id.stepTextView);
        timerTextView = findViewById(R.id.timerTextView);
        Button buttonStart = findViewById(R.id.buttonStart);
        Button buttonCancel = findViewById(R.id.buttonCancel);
        Button buttonVoice = findViewById(R.id.buttonVoice);

        // --- UI와 로직 연결 ---
        // "시작" 버튼은 레시피 시퀀스를 시작합니다.
        buttonStart.setOnClickListener(v -> startDefaultRecipe());
        // "취소" 버튼은 레시피 시퀀스를 중단합니다.
        buttonCancel.setOnClickListener(v -> cancelDefaultRecipe());
        // "음성" 버튼은 음성 명령 입력을 시작합니다.
        buttonVoice.setOnClickListener(v -> voiceCommandHandler.startListening());

        // --- 권한 요청 ---
        askForPermissions();
        resetUi();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 메모리 누수를 방지하기 위해 리소스를 해제합니다.
        if (voiceCommandHandler != null) {
            voiceCommandHandler.destroy();
        }
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
    }

    /**
     * VoiceCommandHandler로부터 오는 콜백 메소드입니다.
     * "start"와 같은 간단한 명령어 문자열을 받아 적절한 액션을 실행합니다.
     */
    @Override
    public void onCommandReceived(String command) {
        switch (command) {
            case "start":
                startDefaultRecipe();
                break;
            case "cancel":
                cancelDefaultRecipe();
                break;
        }
    }

    /**
     * 레시피를 시작하는 방법의 예제입니다.
     */
    private void startDefaultRecipe() {
        // 1. Repository에서 레시피를 가져옵니다.
        currentRecipe = RecipeRepository.getDefaultRecipe();
        // 2. 신뢰성 있는 백그라운드 알람을 시작합니다.
        //    이것이 알람 로직의 가장 중요한 부분입니다.
        RecipeTimer.setAlarm(getApplicationContext(), currentRecipe, 0);
        // 3. 화면에 보이는 포그라운드 타이머를 시작합니다.
        //    이것은 앱이 켜져 있을 때의 사용자 경험을 위한 것입니다.
        startStep(0);
        Toast.makeText(this, "레시피 시작: " + currentRecipe.getName(), Toast.LENGTH_SHORT).show();
    }

    /**
     * 레시피를 취소하는 방법의 예제입니다.
     */
    private void cancelDefaultRecipe() {
        if (currentRecipe != null) {
            // 1. 백그라운드 알람을 취소합니다.
            RecipeTimer.cancelAlarms(getApplicationContext(), currentRecipe);
            Toast.makeText(this, currentRecipe.getName() + " 레시피의 알람이 취소되었습니다.", Toast.LENGTH_SHORT).show();
        }
        // 2. 포그라운드 타이머를 취소합니다.
        if (countDownTimer != null) {
            countDownTimer.cancel();
        }
        resetUi();
    }

    /**
     * 포그라운드 CountDownTimer와 UI 업데이트를 관리합니다.
     */
    private void startStep(int stepIndex) {
        if (currentRecipe == null || stepIndex < 0 || stepIndex >= currentRecipe.getSteps().size()) {
            resetUi();
            return;
        }

        currentStepIndex = stepIndex;
        RecipeStep step = currentRecipe.getSteps().get(currentStepIndex);

        stepTextView.setText(step.getDescription());

        if (countDownTimer != null) {
            countDownTimer.cancel();
        }

        // 현재 단계의 지속 시간 동안 새로운 타이머를 생성하고 시작합니다.
        countDownTimer = new CountDownTimer(step.getDurationInSeconds() * 1000L, 1000) {
            @Override
            public void onTick(long millisUntilFinished) {
                // 매초 타이머 텍스트를 업데이트합니다.
                long minutes = (millisUntilFinished / 1000) / 60;
                long seconds = (millisUntilFinished / 1000) % 60;
                timerTextView.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
            }

            @Override
            public void onFinish() {
                // 이 단계의 포그라운드 타이머가 끝나면, 자동으로 다음 단계를 시작합니다.
                startStep(currentStepIndex + 1);
            }
        }.start();
    }

    /**
     * UI를 초기 상태로 리셋합니다.
     */
    private void resetUi() {
        stepTextView.setText("'레시피 시작'을 누르세요");
        timerTextView.setText("00:00");
        currentStepIndex = -1;
        currentRecipe = null;
    }

    /**
     * 필요한 런타임 권한을 요청하는 로직을 처리합니다.
     */
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
}
