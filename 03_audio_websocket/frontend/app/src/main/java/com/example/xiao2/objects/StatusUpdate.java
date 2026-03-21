package com.example.xiao2.objects;

public class StatusUpdate {
    private final String status;
    private final String Transcript;
    private final String emotion;

    public StatusUpdate(String status, String Transcript, String emotion) {
        this.status = status;
        this.Transcript = Transcript;
        this.emotion = emotion;
    }

    public StatusUpdate(String status, String Transcript) {
        this.status = status;
        this.Transcript = Transcript;
        this.emotion = "neutral";
        new StatusUpdate(status, Transcript,emotion);
    }

    public StatusUpdate(String status) {
        this.status = status;
        this.Transcript = "empty_string";
        this.emotion = "neutral";

        new StatusUpdate(status, Transcript,emotion);
    }

    public String getStatus() {
        return status;
    }
    public String getEmotion() {
        return emotion;
    }

    public String getTranscript() {
        return Transcript;
    }
}

