# Wi‑Fi stream — app pushes song bytes to the robot

LAN only. The song lives on the **app**. The robot does **not** need the file stored in flash. Each `POST` body is the audio.

This document is the app contract for **byte-push playback** (not URL pull).

---

## 1. Endpoint

```
POST http://<robot-ip>/play_wav
Content-Type: audio/wav
Content-Length: <bytes>
```

Body = a complete PCM WAV file (header + samples).

Success:

```json
{"ok":true,"queued":true}
```

Find the robot on Wi‑Fi (same LAN), then POST to its IP. mDNS / discovery may give `http://<name>.local/play_wav`.

---

## 2. Audio format the firmware accepts

| Field | Required |
|--------|----------|
| Container | WAV (`RIFF` … `WAVE`) |
| Codec | PCM only (`audioFormat` 1 or `0xFFFE`) |
| Bits | **16-bit** |
| Channels | **1 (mono) preferred**; stereo is OK if the decoder accepts it — send **mono** to be safe |
| Sample rate | **8000–48000 Hz**. Use **16000 Hz mono** |
| Max body | **384 KiB** (`Content-Length` must be set; `0` or `> 384 KiB` → HTTP 413) |
| MP3 / AAC / raw PCM without WAV header | **Rejected** |

At 16 kHz mono 16-bit: **384 KiB ≈ 12 seconds** of audio (including a small WAV header).

Safe app limit: **≤ 380 KiB** per POST.

---

## 3. Continuous play (long song)

The robot cannot take a whole song in one POST. The **app** splits the song and keeps feeding clips.

### App steps

1. Decode the song on the phone to **PCM 16-bit, 16 kHz, mono**.
2. Keep a playhead: byte offset into that PCM (or sample index).
3. Cut the next slice so the **full WAV** (44-byte header + PCM) is **≤ 380 KiB**.
   - At 16 kHz mono: about **10–11 seconds** of PCM per slice is safe.
4. Wrap **that slice only** in a new WAV header (`RIFF`/`fmt `/`data` with the slice length).
5. `POST /play_wav` with those bytes.
6. Wait until the clip is mostly done, then POST the next slice (see pacing below).
7. Repeat until the song ends or the user pauses.

Do **not** POST the same full file over and over. Each POST is the **next** chunk.

### Pacing (avoid queue pile-up)

`/play_wav` **queues** the whole clip in RAM, then plays it. If the app fires every chunk immediately, the robot can back up several clips and **pause will feel late**.

Recommended:

- Keep **at most 1 clip playing + 1 clip queued**.
- After a successful POST, wait ~**chunk duration minus 0.5–1 s**, then send the next chunk.
- Or poll until the speaker is free (there is **no** dedicated “queue depth” API today; pacing by duration is enough).

There will be a **small gap** between clips. That is a firmware limit of `/play_wav`, not the app.

---

## 4. Pause / stop (app)

There is **no** HTTP “pause `/play_wav`” on this firmware.

### What the app must do on Pause or Stop

1. **Stop posting** further chunks immediately.
2. Remember `playhead` (PCM offset) for Resume. On Stop, you may reset playhead to 0.
3. Do **not** send `POST /music/stop` expecting this path to halt — that only stops **URL music** (`/music/play`), not `/play_wav`.

### What the robot will do today

The **chunk already queued or playing** keeps going until it finishes (up to ~12 s). Then silence, because the app sent nothing more.

That is the only stop behaviour **without a firmware change**.

### Resume

1. If Pause: keep `playhead` where the app estimates playback reached  
   (chunk start + time spent in that chunk is good enough).
2. Build the next WAV from **that offset onward**.
3. `POST /play_wav` again and continue the feed loop.

The robot does **not** store the rest of the song. Resume is **100% app-side**.

---

## 5. Headers (optional)

| Header | Use |
|--------|-----|
| `X-Nino-Eye-Expression` | `happy`, `sad`, … while this clip plays |
| `X-Nino-Prompt-Ack` | `1` only for medical/prompt clips — **do not** set for music |
| `X-Nino-Prompt-Ack-Chime` | only with prompt-ack |
| `X-Nino-Voice-Ws-Url` | server only — **do not** send from the music app |

Music POSTs should be **raw WAV only**, no prompt-ack.

Volume (separate from stream):

```
GET  /speaker/volume
POST /speaker/volume   {"volume": 0-100}
```

---

## 6. Example (one chunk)

```http
POST /play_wav HTTP/1.1
Host: 192.168.0.84
Content-Type: audio/wav
Content-Length: 320044

<320044 bytes: WAV header + ~10 s of 16 kHz mono PCM>
```

```json
{"ok":true,"queued":true}
```

Errors to handle:

| HTTP | Meaning |
|------|---------|
| 405 | Not POST |
| 413 | Missing/`Content-Length` too large |
| 400 | Recv failed |
| 500 | Out of memory |
| 503 | Audio queue not running |

---

## 7. What **not** to use for byte-push

| Endpoint | Why |
|----------|-----|
| `POST /music/play` `{"url":"..."}` | Robot **pulls** a URL. App does not push bytes. |
| `POST /music/stop` | Stops URL music only. |
| Embedded files (`WIFI.wav`, …) | Boot/UI prompts. Irrelevant to app songs. |

---

## 8. Firmware needed? (honest)

| App want | Firmware change? |
|----------|------------------|
| Push WAV bytes and play | **No** — `/play_wav` exists |
| Long song via chunks | **No** — app splits; small gaps between clips |
| Pause = stop sending more audio | **No** — app stops POSTing |
| Pause/Stop **cuts speaker now** | **Yes** — no HTTP flush of `/play_wav` queue |
| Gapless continuous stream | **Yes** — `/play_wav` is clip-at-a-time |
| True pause/resume inside the robot | **Yes** — robot never keeps the full song |

**Ship the app on `/play_wav` chunks first.** Add a stop API later only if cutting audio within ~1 s matters.

Suggested later firmware (not required for first app):

```
POST /play_wav/stop
```

would preempt the playback queue so Pause/Stop is immediate. Resume still stays in the app (playhead + next chunk).
