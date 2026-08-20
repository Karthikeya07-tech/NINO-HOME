# Wi‑Fi stream — app pushes song bytes to the robot

LAN only. The song lives on the **app**. The robot does **not** need the file stored in flash. Each `POST` body is the next audio chunk.

This document is the app contract for **byte-push playback** (not URL pull).

Firmware on this path now supports **gapless clip hand-off** and **real-time pause/resume**. The app still must prefetch chunks and keep feeding while the song plays — including when the app is in the background.

---

## 1. Endpoints

### Play (push a chunk)

```
POST http://<robot-ip>/play_wav
Content-Type: audio/wav
Content-Length: <bytes>
X-Nino-Stream: 1
```

Body = a complete PCM WAV file (header + this slice’s samples).

**`X-Nino-Stream: 1` is required for music.** Without it the speaker closes after each clip and you will hear a gap. Do not send this header for voice/TTS prompts.

Success:

```json
{"ok":true,"queued":true}
```

Find the robot on Wi‑Fi (same LAN), then POST to its IP. mDNS / discovery may give `http://<name>.local/play_wav`.

### Pause / resume / stop / status

| Method | Path | Body | Effect |
|--------|------|------|--------|
| `POST` | `/play_wav/pause` | empty | Cuts the speaker **now**. Remaining PCM of the current clip stays on the robot. |
| `POST` | `/play_wav/resume` | empty | Continues from the exact pause point, then any already-queued clips. |
| `POST` | `/play_wav/stop` | empty | Silences now. Drops queued + paused audio. Playhead is gone. |
| `GET`  | `/play_wav/status` | — | Queue snapshot (see below). |

Pause / resume / stop success examples:

```json
{"ok":true,"paused":true}
{"ok":true,"paused":false}
{"ok":true,"stopped":true}
```

Status:

```json
{"ok":true,"playing":true,"paused":false,"suspended":false,"queued":1}
```

| Field | Meaning |
|--------|---------|
| `playing` | Speaker is outputting stream audio right now |
| `paused` | Last command was pause; speaker is silent; leftover PCM is held |
| `suspended` | Current clip was interrupted mid-way (pause). Resume will continue it. |
| `queued` | Whole clips waiting behind the current one |

Do **not** use `POST /music/stop` for this path. That only stops URL-pull music (`/music/play`).

---

## 2. Audio format the firmware accepts

| Field | Required |
|--------|----------|
| Container | WAV (`RIFF` … `WAVE`) |
| Codec | PCM only (`audioFormat` 1 or `0xFFFE`) |
| Bits | **16-bit** |
| Channels | **1 (mono) preferred**; send **mono** |
| Sample rate | **8000–48000 Hz**. Use **16000 Hz mono** |
| Max body | **384 KiB** (`Content-Length` must be set; `0` or `> 384 KiB` → HTTP 413) |
| MP3 / AAC / raw PCM without WAV header | **Rejected** |

At 16 kHz mono 16-bit: **384 KiB ≈ 12 seconds** of audio (including a small WAV header).

Safe app limit: **≤ 380 KiB** per POST.

---

## 3. Continuous / gapless play (long song)

The robot cannot take a whole song in one POST. The **app** splits the song and keeps feeding clips.

The gap you heard before was **firmware**: `/play_wav` closed the DAC after every clip. That is fixed **when** the app:

1. Sends `X-Nino-Stream: 1` on every music chunk.
2. **Prefetches** so the next chunk is already queued **before** the current one finishes.

If the queue is empty when a clip ends, the robot waits up to **~400 ms** for the next POST, then closes the speaker. A late POST after that will play, but you will hear a gap. Prefetch removes that.

### App steps

1. Decode the song on the phone to **PCM 16-bit, 16 kHz, mono**.
2. Keep a playhead: byte offset into that PCM (or sample index). This is only for **building the next slice**, not for pause (the robot owns pause position).
3. Cut the next slice so the **full WAV** (44-byte header + PCM) is **≤ 380 KiB**.
   - At 16 kHz mono: about **10–11 seconds** of PCM per slice is safe.
4. Wrap **that slice only** in a new WAV header (`RIFF`/`fmt `/`data` with the slice length).
5. `POST /play_wav` with `X-Nino-Stream: 1`.
6. Immediately prepare and POST the **next** slice so the robot always has **1 playing + 1 queued** (optionally 2 queued).
7. Repeat until the song ends, the user pauses, or the user stops.

Do **not** POST the same full file over and over. Each POST is the **next** chunk.

### Prefetch / pacing

Keep **at most 1 clip playing + 1 or 2 clips queued**.

- After the first successful POST, send the second chunk **immediately** (do not wait for the first to finish).
- Then send chunk N+1 when `GET /play_wav/status` shows `queued` dropped to 0 or 1, **or** after ~**(chunk duration − 1.5 s)** from the previous send.
- Do not dump the whole song into the queue. Each clip uses up to ~384 KiB of robot RAM.

Poll example:

```
GET /play_wav/status
```

If `paused` is `true`, **do not** send more chunks until resume (in-flight POSTs that already arrived will sit in `queued` and play after resume).

---

## 4. Pause / resume (real time)

Pause and resume are **firmware**. The app must **not** try to fake pause by stopping POSTs and later re-sending the current clip.

### Pause

1. `POST /play_wav/pause` as soon as the user hits pause (empty body).
2. **Stop posting** further chunks.
3. Do **not** re-send the clip that was playing. The robot already holds the remaining PCM (`suspended: true`).
4. Speaker should go silent within ~30 ms.

The app playhead does **not** need to be updated for pause. Resume is not “guess how far we got.”

### Resume

1. `POST /play_wav/resume` (empty body).
2. Playback continues from the pause point, then any clips already in `queued`.
3. Resume the prefetch loop (next **unsent** slice of the song, not the paused slice).

Track two offsets on the app:

| Offset | Use |
|--------|-----|
| `next_slice_start` | PCM byte where the **next POST** should begin (advanced only when a chunk is successfully queued) |
| (robot) pause point | Owned by firmware. App never seeks backwards into an already-POSTed clip on resume |

### Stop

1. `POST /play_wav/stop`.
2. Stop posting. Reset `next_slice_start` to 0 if the user starts the same song again.

---

## 5. App in background — keep streaming until the song ends

The robot only plays what it has already received. It does **not** pull the rest of the file from the phone. If the app is suspended by the OS and stops POSTing, playback continues only for the **already queued** clips (~12–24 s if you kept 1–2 chunks), then silence.

**Required app behaviour:** keep the feed loop alive until the last chunk is POSTed, even if the UI is not on screen.

### Android

- Run the stream as a **foreground service** with a persistent notification (e.g. “Playing on Nino”).
- Keep Wi‑Fi / HTTP POSTs running in that service, not only in an Activity.
- Pause/resume/stop buttons on the notification should call the same `/play_wav/pause|resume|stop` endpoints.
- Do **not** rely on the Activity remaining started. `onPause` / `onStop` of the UI must **not** cancel the feed unless the user hit Pause or Stop.
- If the user **force-stops** the app or the service is killed, call `/play_wav/stop` if you still can; otherwise the robot will finish whatever is already queued.

### iOS

- Enable **Background Modes → Audio** (and keep an audio session active) so the process is not frozen while feeding the robot.
- Continue POSTing chunks from a background-capable player/session until the song is fully sent.
- Same pause/resume/stop HTTP calls as foreground.
- If the user swipes the app away, iOS may kill the process. Anything not yet POSTed will not play. Prefetch 1–2 chunks so a short kill still finishes a few seconds on the robot.

### Both platforms

- Screen off / app in recents: **keep POSTing**.
- User Pause: `POST /play_wav/pause` and stop POSTing; service may stay alive.
- User Stop or “end song”: `POST /play_wav/stop` and stop the background work.
- Phone leaves the LAN: POSTs will fail; stop the loop and surface an error. The robot will play queued audio then stop.

The firmware does **not** need the app UI visible. It only needs HTTP on the same Wi‑Fi.

---

## 6. Headers

| Header | Use |
|--------|-----|
| **`X-Nino-Stream: 1`** | **Required for music chunks** — gapless speaker path, no servo dance |
| `X-Nino-Eye-Expression` | optional `happy`, `sad`, … while this clip plays |
| `X-Nino-Prompt-Ack` | `1` only for medical/prompt clips — **do not** set for music |
| `X-Nino-Prompt-Ack-Chime` | only with prompt-ack |
| `X-Nino-Voice-Ws-Url` | server only — **do not** send from the music app |

Music POSTs: WAV body + `X-Nino-Stream: 1` only.

Volume (separate from stream):

```
GET  /speaker/volume
POST /speaker/volume   {"volume": 0-100}
```

---

## 7. Example (one chunk)

```http
POST /play_wav HTTP/1.1
Host: 192.168.0.84
Content-Type: audio/wav
Content-Length: 320044
X-Nino-Stream: 1

<320044 bytes: WAV header + ~10 s of 16 kHz mono PCM>
```

```json
{"ok":true,"queued":true}
```

Pause:

```http
POST /play_wav/pause HTTP/1.1
Host: 192.168.0.84
Content-Length: 0
```

Errors to handle:

| HTTP | Meaning |
|------|---------|
| 405 | Wrong method |
| 413 | Missing/`Content-Length` too large |
| 400 | Recv failed |
| 500 | Out of memory |
| 503 | Audio queue not running |

---

## 8. What **not** to use for byte-push

| Endpoint | Why |
|----------|-----|
| `POST /music/play` `{"url":"..."}` | Robot **pulls** a URL. App does not push bytes. |
| `POST /music/stop` | Stops URL music only, **not** `/play_wav`. Use `/play_wav/stop`. |
| Embedded files (`WIFI.wav`, …) | Boot/UI prompts. Irrelevant to app songs. |

---

## 9. Who owns what

| Want | App | Firmware |
|------|-----|----------|
| Push WAV bytes and play | POST chunks | `/play_wav` |
| Long song | Split + prefetch 1–2 chunks | Plays queue |
| No gap between chunks | `X-Nino-Stream: 1` + prefetch | Keeps DAC open between clips |
| Pause cuts speaker now | `POST /play_wav/pause` | Stops within ~30 ms, holds PCM |
| Resume from same place | `POST /play_wav/resume` (do not re-send current clip) | Continues from offset |
| Stop | `POST /play_wav/stop` | Flushes queue |
| App in background until song ends | Foreground service / background audio; keep POSTing | Plays whatever was received |

Voice wake on the robot can still preempt speaker audio. That is independent of the music app.
