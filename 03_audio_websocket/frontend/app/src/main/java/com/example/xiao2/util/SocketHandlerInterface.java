package com.example.xiao2.util;

import androidx.lifecycle.LiveData;

/**
 * Interface for handling WebSocket communication.
 * Follows the Interface Segregation Principle for better MVVM architecture.
 */
public interface SocketHandlerInterface {
    /**
     * Send text message to the server
     * @param message The message to send
     */
    void sendMessage(String message);
    
    /**
     * Send audio data to the server
     * @param audioData The audio data as byte array
     */
    void sendAudioData(byte[] audioData);
    
    /**
     * Send image with message to the server
     * @param message Text message
     * @param imageBase64 Base64 encoded image
     */
    void sendMessageWithImage(String message, String imageBase64);
    
    /**
     * Send user information to the server
     * @param userName User's name
     * @param userId User's ID
     * @param personality User's personality type
     * @param channel Channel to join
     */
    void sendUserInfo(String userName, String userId, String personality, String channel);
    
    /**
     * Get incoming audio stream
     * @return LiveData containing audio data
     */
    LiveData<byte[]> getIncomingAudio();
    
    /**
     * Connect to the WebSocket server
     */
    void connect();
    
    /**
     * Disconnect from the WebSocket server
     */
    void disconnect();
    
    /**
     * Check if connected to server
     * @return true if connected, false otherwise
     */
    boolean isConnected();
    
    /**
     * Start streaming audio
     */
    void startStreaming();
    
    /**
     * Stop streaming audio
     */
    void stopStreaming();

    /**
     * login
     */
    boolean login(String userId, String userName, String password, boolean newChat, String personality);


}
