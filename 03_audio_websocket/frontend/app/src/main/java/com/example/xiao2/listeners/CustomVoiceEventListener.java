package com.example.xiao2.listeners;

import android.util.Log;

import com.example.xiao2.objects.StatusUpdate;
import com.example.xiao2.repository.DataRepository;
import com.nuwarobotics.service.agent.VoiceEventListener;
import com.nuwarobotics.service.agent.VoiceResultJsonParser;

import org.json.JSONException;
import org.json.JSONObject;

public class CustomVoiceEventListener implements VoiceEventListener {

    private static final String TAG = "CustomVoiceEventListener";

    public CustomVoiceEventListener(DataRepository dataRepository) {
    }

    @Override
    public void onWakeup(boolean isError, String score, float direction) {}

    @Override
    public void onTTSComplete(boolean isError) {}

    @Override
    public void onSpeechRecognizeComplete(boolean isError, ResultType iFlyResult, String json) {}

    @Override
    public void onSpeech2TextComplete(boolean isError, String json) {
    }

    @Override
    public void onMixUnderstandComplete(boolean isError, ResultType resultType, String s) {}


    @Override
    public void onSpeechState(ListenType listenType, SpeechState speechState) {}

    @Override
    public void onSpeakState(SpeakType speakType, SpeakState speakState) {}

    @Override
    public void onGrammarState(boolean isError, String s) {
    }

    @Override
    public void onListenVolumeChanged(ListenType listenType, int i) {}

    @Override
    public void onHotwordChange(HotwordState hotwordState, HotwordType hotwordType, String s) {}

}
