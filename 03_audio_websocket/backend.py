#!/usr/bin/env python3
"""
Minimal Python backend for 03_audio_websocket.
- WebSocket server on port 8765  (audio streaming)
- TCP login server on port 12345 (authentication)

Audio format: 24kHz, mono, 16-bit PCM, Base64-encoded chunks
"""

import asyncio
import base64
import json
import math
import struct

import websockets

HOST = "0.0.0.0"
WS_PORT = 8765
LOGIN_PORT = 12345
SAMPLE_RATE = 24000


def generate_sine_wave(freq: float = 440.0, duration: float = 1.0) -> bytes:
    """Return 16-bit PCM bytes for a sine wave (24kHz mono)."""
    n = int(SAMPLE_RATE * duration)
    return b"".join(
        struct.pack("<h", int(32767 * math.sin(2 * math.pi * freq * i / SAMPLE_RATE)))
        for i in range(n)
    )


# ── WebSocket handler ────────────────────────────────────────────────────────

async def handle_websocket(websocket):
    addr = websocket.remote_address
    print(f"[WS] Connected: {addr}")
    audio_buffer = bytearray()

    try:
        async for raw in websocket:
            try:
                msg = json.loads(raw)
            except json.JSONDecodeError:
                print(f"[WS] Non-JSON message ignored")
                continue

            msg_type = msg.get("type", "")
            print(f"[WS] ← {msg_type}")

            match msg_type:

                case "input_audio_buffer.start":
                    audio_buffer.clear()

                case "input_audio_buffer.append":
                    chunk = base64.b64decode(msg.get("audio", ""))
                    audio_buffer.extend(chunk)
                    print(f"[WS]   buffered {len(chunk)} B (total {len(audio_buffer)} B)")

                case "input_audio_buffer.stop":
                    print(f"[WS]   received {len(audio_buffer)} B of audio")
                    await _send_audio_response(websocket)
                    audio_buffer.clear()

                case "user.login":
                    print(f"[WS]   user={msg.get('user_name')} id={msg.get('user_id')}")
                    await websocket.send(json.dumps({"type": "user.login.success"}))

                case "input.message":
                    text = msg.get("message", "")
                    print(f"[WS]   text message: {text!r}")
                    await websocket.send(json.dumps({
                        "type": "response.emotion_transcript",
                        "emotion": "neutral",
                        "transcript": f"收到：{text}",
                    }))

                case "pong":
                    pass  # keep-alive reply, no action needed

                case "client.disconnect":
                    print(f"[WS]   client requested disconnect")
                    break

                case _:
                    print(f"[WS]   unknown type: {msg_type!r}")

    except websockets.exceptions.ConnectionClosed as e:
        print(f"[WS] Disconnected: {addr} ({e.code})")


async def _send_audio_response(websocket):
    """Send a 1-second 440 Hz tone back as audio.delta + done + emotion."""
    pcm = generate_sine_wave(440, 1.0)
    await websocket.send(json.dumps({
        "type": "response.audio.delta",
        "delta": base64.b64encode(pcm).decode(),
    }))
    await websocket.send(json.dumps({"type": "response.audio.done"}))
    await websocket.send(json.dumps({
        "type": "response.emotion_transcript",
        "emotion": "neutral",
        "transcript": "（測試音訊回應）",
    }))


# ── TCP login handler ────────────────────────────────────────────────────────

async def handle_tcp_login(reader: asyncio.StreamReader, writer: asyncio.StreamWriter):
    addr = writer.get_extra_info("peername")
    print(f"[TCP] Login from {addr}")
    try:
        data = await reader.read(4096)
        req = json.loads(data.decode())
        print(f"[TCP]   user={req.get('user_name')} new_chat={req.get('new_chat')}")
        writer.write((json.dumps({"is_login": True}) + "\n").encode())
        await writer.drain()
    except Exception as e:
        print(f"[TCP] Error: {e}")
        writer.write((json.dumps({"is_login": False}) + "\n").encode())
        await writer.drain()
    finally:
        writer.close()


# ── Entry point ──────────────────────────────────────────────────────────────

async def main():
    ws_server = await websockets.serve(handle_websocket, HOST, WS_PORT)
    tcp_server = await asyncio.start_server(handle_tcp_login, HOST, LOGIN_PORT)
    print(f"[*] WebSocket : ws://0.0.0.0:{WS_PORT}")
    print(f"[*] TCP login : 0.0.0.0:{LOGIN_PORT}")
    async with ws_server, tcp_server:
        await asyncio.Future()  # run forever


if __name__ == "__main__":
    asyncio.run(main())