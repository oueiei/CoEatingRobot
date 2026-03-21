# 3Xiao Android Frontend

## Overview

The 3Xiao Android Frontend is a mobile application that enables real-time duplex audio communication with a remote WebSocket server. This app follows the MVVM (Model-View-ViewModel) design pattern and implements a robust WebSocket-based communication system for sending and receiving audio data.

## Key Features

- Real-time bidirectional audio streaming with WebSockets
- Voice activity detection to filter out background noise
- Automatic connection management with retry mechanisms
- Emotion-based robot responses with synchronized audio playback
- Clean MVVM architecture with separation of concerns

## Technical Details

### Audio Configuration

- Sample Rate: 24kHz mono audio (16-bit PCM)
- Chunk Size: 1024 frames for optimal real-time performance
- Voice Detection: Energy-based detection with tunable threshold

### WebSocket Communication

- Protocol: Standard WebSocket (RFC 6455)
- Message Format: JSON-based events with Base64-encoded audio
- Events: Support for various event types including audio deltas, emotion transcripts, etc.
- Connection Management: Automatic reconnection with exponential backoff

### MVVM Architecture

- **Views**: Activity and fragments for UI display
- **ViewModels**: RobotViewModel coordinates between views and data
- **Repository**: DataRepository acts as the single source of truth
- **Services**: WebSocketHandler manages real-time communication

## Components

- **WebSocketHandler**: Manages WebSocket connections and message handling
- **CustomAudioManager**: Handles audio recording and playback with low latency
- **DataRepository**: Coordinates data flow and manages application state
- **RobotViewModel**: Processes UI events and updates the view
- **Interface Segregation**: Clean interfaces for better testability

## Connection Diagram

```
┌─────────────┐      ┌─────────────┐      ┌─────────────┐
│    View     │◄────►│  ViewModel  │◄────►│ Repository  │
└─────────────┘      └─────────────┘      └──────┬──────┘
                                                  │
                                          ┌───────▼───────┐
                                          │WebSocketHandler│
                                          └───────┬───────┘
                                                  │
                                          ┌───────▼───────┐
                                          │ Remote Server │
                                          └───────────────┘
```

## Message Flow

1. **Recording**: Audio is captured, chunked, and encoded
2. **Sending**: Base64-encoded audio chunks are sent to the server
3. **Receiving**: Server responds with audio data and emotion transcripts
4. **Playing**: Received audio is decoded and played with proper timing

## Setup and Configuration

The WebSocket connection URL can be configured in the WebSocketHandler class:

```java
private static final String SERVER_HOST = "localhost";
private static final int SERVER_PORT = 8765;
```

## Performance Optimizations

- Background thread execution for audio processing
- Voice activity detection to reduce bandwidth
- Chunked audio playback for smoother streaming
- Echo cancellation to prevent feedback loops
- Efficient memory usage with buffer recycling

## Requirements

- Android API level 23+ (Android 6.0 Marshmallow)
- Internet permission for WebSocket communication
- Microphone permission for audio recording

## Best Practices

- All network operations are performed on background threads
- LiveData is used for reactive UI updates
- Interface-based design for better testability
- Proper resource cleanup in lifecycle methods