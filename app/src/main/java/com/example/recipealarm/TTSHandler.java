package com.example.recipealarm;

import android.content.Context;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import java.util.Locale;

/**
 * TTS(Text-to-Speech) 출력을 간편하게 처리하기 위한 헬퍼 클래스입니다.
 * 이 클래스의 인스턴스는 "fire-and-forget" 방식으로 동작하도록 설계되었습니다.
 * 인스턴스를 생성하고 speak() 메소드를 호출하면, 음성 출력이 완료된 후 스스로 리소스를 해제합니다.
 * BroadcastReceiver와 같이 생명주기가 짧은 컴포넌트에서 사용하기에 적합합니다.
 */
public class TTSHandler implements TextToSpeech.OnInitListener {

    private static final String TAG = "TTSHandler";
    private TextToSpeech tts;
    private final String textToSpeak;
    private final Context context;

    /**
     * TTSHandler를 생성하고 음성 출력을 준비합니다.
     * @param context 애플리케이션 컨텍스트
     * @param textToSpeak 음성으로 변환할 텍스트
     */
    public TTSHandler(Context context, String textToSpeak) {
        this.context = context;
        this.textToSpeak = textToSpeak;
        // TextToSpeech 엔진을 초기화합니다. 초기화가 완료되면 onInit 콜백이 호출됩니다.
        this.tts = new TextToSpeech(context, this);
    }

    /**
     * TextToSpeech 엔진이 초기화되었을 때 호출되는 콜백 메소드입니다.
     * @param status 초기화 상태 (TextToSpeech.SUCCESS 또는 TextToSpeech.ERROR)
     */
    @Override
    public void onInit(int status) {
        if (status == TextToSpeech.SUCCESS) {
            // 언어 설정 (한국어)
            int result = tts.setLanguage(Locale.KOREAN);

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e(TAG, "한국어 TTS가 지원되지 않습니다.");
                // 지원되지 않아도 소멸 로직은 타야 하므로 speak를 호출합니다.
                speakNow();
            } else {
                // 음성 출력 시작
                speakNow();
            }
        } else {
            Log.e(TAG, "TTS 엔진 초기화 실패");
            // 초기화에 실패해도 리소스를 해제해야 합니다.
            shutdown();
        }
    }

    /**
     * 실제 음성 출력을 실행하고, 출력이 끝나면 리소스를 해제하도록 설정합니다.
     */
    private void speakNow() {
        // 음성 출력이 언제 끝나는지 감지하기 위해 리스너를 설정합니다.
        tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
            @Override
            public void onStart(String utteranceId) {
                // 음성 출력이 시작됨
            }

            @Override
            public void onDone(String utteranceId) {
                // 음성 출력이 완료되면 TTS 엔진 리소스를 해제합니다.
                shutdown();
            }

            @Override
            public void onError(String utteranceId) {
                // 오류가 발생해도 리소스를 해제합니다.
                shutdown();
            }
        });

        // 음성 출력을 요청합니다. utteranceId는 "SelfDestruct"로 지정하여 콜백에서 식별할 수 있도록 합니다.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            tts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "SelfDestruct");
        } else {
            tts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null);
        }
    }

    /**
     * TextToSpeech 엔진을 안전하게 종료하고 리소스를 해제합니다.
     */
    private void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            Log.d(TAG, "TTS 엔진이 소멸되었습니다.");
        }
    }
}
