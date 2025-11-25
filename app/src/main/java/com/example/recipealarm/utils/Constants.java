package com.example.recipealarm.utils;

/**
 * 앱 전역에서 사용되는 상수들을 모아놓은 클래스
 */
public class Constants {
    // Intent Extras
    public static final String EXTRA_RECIPE_ID = "com.example.recipealarm.EXTRA_RECIPE_ID";
    
    // TimerService Actions
    public static final String ACTION_START_TIMER = "com.example.recipealarm.ACTION_START_TIMER";
    public static final String ACTION_STOP_TIMER = "com.example.recipealarm.ACTION_STOP_TIMER";
    public static final String ACTION_PAUSE_TIMER = "com.example.recipealarm.ACTION_PAUSE_TIMER";
    public static final String ACTION_RESUME_TIMER = "com.example.recipealarm.ACTION_RESUME_TIMER";
    public static final String ACTION_NAVIGATE_STEP = "com.example.recipealarm.ACTION_NAVIGATE_STEP";
    public static final String ACTION_TIMER_UPDATE = "com.example.recipealarm.ACTION_TIMER_UPDATE";
    public static final String ACTION_TIMER_FINISH = "com.example.recipealarm.ACTION_TIMER_FINISH";
    
    // TimerService Extras
    public static final String EXTRA_STEP_DESCRIPTION = "EXTRA_STEP_DESCRIPTION";
    public static final String EXTRA_TIME_REMAINING_FORMATTED = "EXTRA_TIME_REMAINING_FORMATTED";
    public static final String EXTRA_STEP_INDEX = "EXTRA_STEP_INDEX";
    public static final String EXTRA_TOTAL_STEPS = "EXTRA_TOTAL_STEPS";
    public static final String EXTRA_TIME_REMAINING_MS = "EXTRA_TIME_REMAINING_MS";
    public static final String EXTRA_STEP_DURATION_MS = "EXTRA_STEP_DURATION_MS";
    public static final String EXTRA_IS_PAUSED = "EXTRA_IS_PAUSED";
    public static final String EXTRA_NAVIGATE_DIRECTION = "EXTRA_NAVIGATE_DIRECTION"; // "prev" or "next"
    
    private Constants() {
        // 인스턴스화 방지
    }
}

