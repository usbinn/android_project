package com.example.recipealarm;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;

import java.util.ArrayList;

/**
 * 안드로이드의 SpeechRecognizer를 사용하여 음성 인식을 처리하는 클래스입니다.
 * 이 클래스는 음성 입력을 듣고, 간단한 명령어로 분석한 뒤,
 * 콜백을 사용하여 인식된 명령을 UI에 알립니다.
 */
public class VoiceCommandHandler implements RecognitionListener {

    private static final String TAG = "VoiceCommandHandler";
    private final SpeechRecognizer speechRecognizer;
    private final Intent speechRecognizerIntent;
    private final VoiceCommandCallback callback;

    /**
     * 명령어가 인식되었을 때 호출 컨텍스트(예: 액티비티)에 알리기 위한 콜백 인터페이스입니다.
     */
    public interface VoiceCommandCallback {
        /**
         * 지원되는 명령어가 감지되었을 때 호출됩니다.
         * @param command 명령어를 나타내는 간단한 문자열 (예: "start", "cancel").
         */
        void onCommandReceived(String command);
    }

    /**
     * SpeechRecognizer를 초기화합니다.
     * @param context 애플리케이션 컨텍스트.
     * @param callback 명령어가 인식되었을 때 호출될 콜백.
     */
    public VoiceCommandHandler(Context context, VoiceCommandCallback callback) {
        this.callback = callback;
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context);
        if (speechRecognizer != null) {
            speechRecognizer.setRecognitionListener(this);
        }

        speechRecognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechRecognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR"); // 한국어 설정
    }

    /**
     * 음성 입력을 듣기 시작합니다.
     * 사용자에게 음성 인식을 위한 시스템 UI가 표시됩니다.
     */
    public void startListening() {
        if (speechRecognizer != null) {
            speechRecognizer.startListening(speechRecognizerIntent);
        }
    }

    /**
     * 음성 입력을 중지합니다.
     */
    public void stopListening() {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
        }
    }

    /**
     * SpeechRecognizer 리소스를 해제합니다.
     * 이 메소드는 호스팅하는 액티비티나 프래그먼트의 onDestroy()에서 호출되어야 합니다.
     */
    public void destroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
    }

    @Override
    public void onResults(Bundle results) {
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (matches != null && !matches.isEmpty()) {
            String bestMatch = matches.get(0).toLowerCase();
            Log.d(TAG, "Recognized text: " + bestMatch);
            // 간단한 명령어 분석. 실제 앱에서는 더 정교하게 만들 수 있습니다.
            if (bestMatch.contains("시작")) {
                callback.onCommandReceived("start");
            } else if (bestMatch.contains("취소")) {
                callback.onCommandReceived("cancel");
            }
        }
    }

    // 다른 RecognitionListener 메소드들은 디버깅과 완전성을 위해 구현되었습니다.
    @Override
    public void onReadyForSpeech(Bundle params) { Log.d(TAG, "onReadyForSpeech"); }

    @Override
    public void onBeginningOfSpeech() { Log.d(TAG, "onBeginningOfSpeech"); }

    @Override
    public void onRmsChanged(float rmsdB) { /* 신경쓰지 않음 */ }

    @Override
    public void onBufferReceived(byte[] buffer) { /* 신경쓰지 않음 */ }

    @Override
    public void onEndOfSpeech() { Log.d(TAG, "onEndOfSpeech"); }

    @Override
    public void onError(int error) { Log.e(TAG, "onError: " + error); }

    @Override
    public void onPartialResults(Bundle partialResults) { /* 신경쓰지 않음 */ }

    @Override
    public void onEvent(int eventType, Bundle params) { /* 신경쓰지 않음 */ }
}
