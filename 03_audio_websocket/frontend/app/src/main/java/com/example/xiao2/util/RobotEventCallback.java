package com.example.xiao2.util;

public interface RobotEventCallback {
    void postToUiThread(Runnable runnable);
//    void resetUIToInitialState();
}