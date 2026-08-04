# Record & Play Guide

User guide for teaching Dynamixel head motions from the **Nino Home** Android app, saving them as named **actions**, and playing them back on the bot.

---

## How to open Record and Play

1. Open the app on the **Create New / Home - Scenes** tab.
2. Make sure your Nino device is on the same Wi‑Fi (it should appear in the device list).
3. Tap the **⋮** (three vertical dots) in the top-right of the Home - Scenes bar.
4. Tap **Record and Play**.

This opens the **Actions** page.

---

## Overview

| Concept | Meaning |
|---------|---------|
| **Frame** | One snapshot of motor position(s) (Tilt ID1 and/or Pan ID2), with a hold time |
| **Action** | An ordered list of frames joined end-to-end |
| **Neutral** | Center pose for both motors (`512` on the 0–1023 AX scale) |
| **Storage** | Actions are saved on the phone; the bot receives frames only when you Play |

---

## Actions page (Record and Play)

This is the first screen after opening Record and Play.

### Device line

Shows which bot will be used (`IP:port`). If no device is found, connect the bot to home Wi‑Fi and refresh discovery from Home first.

### ? (help) button

- Circle with **?** in the top-right of the Actions page.
- Opens the in-app **Record & Play Guide** with the full creation / record / play instructions.

### New Action

- Opens the **Action Editor** with an empty frame list.
- Use this to teach a new motion.

### Stop Play

- Appears while an action is playing.
- Sends `POST /servo/play/stop` so the bot stops the current playback.

### Saved action cards

Each saved action shows:

- **Name**
- Frame count, total duration (sum of `hold_ms`), and motors used

#### Play

- Sends the action’s joined frames to the bot: `POST /servo/play`
- Bot moves motors frame by frame using each frame’s `hold_ms`

#### Edit

- Opens the Action Editor with that action’s name, motors, and frames loaded

#### Rename

- Opens a dialog to change only the action name (max ~40 characters)
- Frame data stays the same

#### Delete

- Removes the whole action from phone storage after confirmation

---

## Action Editor

Build or edit one action: name it, choose motors, capture frames, then Save or Play.

### Action name (text field)

- Type or edit the action name.
- Required before **Save Action**.
- Empty names are blocked.

### Motors chips

| Chip | Motors used when capturing / playing |
|------|--------------------------------------|
| **Tilt** | ID1 only (head up / down) |
| **Pan** | ID2 only (head left / right) |
| **Both** | ID1 and ID2 |

Capture and Neutral use the currently selected chip(s).

### Live positions card

Shows live readout while in record mode (polled from `GET /servo/position`):

- **Tilt (ID1):** current raw position
- **Pan (ID2):** current raw position

#### Enter Record Mode

- First moves selected motors to **neutral `512`** via `POST /servo/goal`
- Then calls `POST /servo/record` with `{ "action": "start", "ids": [...], "torque_off": true }`
- Turns torque **off** on selected motors so you can move the head by hand
- Starts live position polling
- Face tracking / other head motion owners should pause on the bot while recording

#### Leave Record Mode

- Calls `POST /servo/record` with `{ "action": "stop" }`
- Stops live polling
- Bot restores torque / leaves record mode

#### Neutral

- Moves selected motors to **neutral position `512`**
- Sends `POST /servo/goal` for each selected motor id with `position: 512`
- Useful to reset the head while editing
- Enter Record Mode already goes to neutral automatically; use this button anytime later
- Works whether or not you are in record mode (bot must be online)

---

## Frames strip

Horizontal chips: `F0`, `F1`, `F2`, …

- Tap a chip to **select** that frame
- Selected frame is highlighted
- Order is the playback order (joined sequence)

### Selected frame detail

When a frame is selected:

- Shows motor positions stored in that frame
- **Hold (ms)** controls how long the bot spends moving to / holding this pose before the next frame

| Control | Function |
|---------|----------|
| **-100** | Decrease hold by 100 ms (min 0) |
| **+100** | Increase hold by 100 ms |
| **500** | Set hold to 500 ms (default for new non-first frames) |
| **0** | Set hold to 0 ms (snap / start pose style) |

Frame 0 often uses `hold_ms = 0` as the start pose.

---

## Edit frames buttons

These change the editable frame list in the app. The bot only receives frames on Play / Preview / Neutral / Record mode.

### Add Frame

- Snapshots the **current live pose** of selected motors
- Appends a new frame at the **end** of the list
- First frame default hold = `0`; later frames default hold = `500` ms
- Needs live positions (enter record mode and wait for readout)

### Insert Before

- Inserts current live pose **before** the selected frame
- List reindexes after insert

### Insert After

- Inserts current live pose **after** the selected frame
- List reindexes after insert

### Replace

- Overwrites the **selected** frame’s positions with the current live pose
- Keeps that frame’s existing `hold_ms`

### Delete Frame

- Removes the selected frame
- Remaining frames reindex (`F0…Fn`)

### Preview

- Sends the selected frame’s pose to the bot via `POST /servo/goal` (per motor)
- Lets you check one pose before full Play
- Does not save anything

---

## Playback & save (editor bottom)

### Play Action

- Plays the **current editor frames** on the bot (`POST /servo/play`) even if not saved yet
- Requires at least one frame and a connected device

### Stop Play

- Stops an in-progress play (`POST /servo/play/stop`)

### Save Action

- Saves the named action + frames to **phone local storage**
- Updates the Actions list
- Also leaves record mode if it was still on
- Requires a non-empty name and at least one frame

---

## My Music → Actions page

Saved actions also appear under:

**My Music → open a device → bottom tab Actions**

### What you see

- Only the **action name** for each saved action
- Empty state: “No saved actions yet.”

### Tap an action name

- Plays that saved action on the connected bot (`POST /servo/play`)
- Same frame data as Record and Play storage

Create / edit / rename / delete still happen in **Record and Play**. My Music Actions is a quick play list of names.

---

## Typical workflow

1. Home - Scenes → ⋮ → **Record and Play**
2. Tap **New Action**, name it (example: `Wave`)
3. Choose motors (**Both** for full head motion)
4. Tap **Enter Record Mode** (motors auto-move to Neutral `512`, then hand-teach starts)
5. Optionally tap **Neutral** later if you want to re-center
6. Move the head by hand → **Add Frame** → repeat
7. Fix mistakes with Insert / Replace / Delete and hold time
8. Tap **Preview** on a selected frame if needed
9. Tap **Save Action**
10. From Actions list (or My Music → Actions), tap **Play**
11. Use the **?** button on the Actions page anytime for the in-app guide

---

## Firmware APIs used by these buttons

| Button / action | API |
|-----------------|-----|
| Enter Record Mode | `POST /servo/goal` → 512, then `POST /servo/record` `{ action: "start", ids, torque_off: true }` |
| Leave Record Mode | `POST /servo/record` `{ action: "stop" }` |
| Live angles | `GET /servo/position` (polled) |
| Neutral | `POST /servo/goal` `{ id, position: 512, speed }` |
| Preview | `POST /servo/goal` for selected frame poses |
| Play / Play Action / My Music tap | `POST /servo/play` `{ name, speed, frames[] }` |
| Stop Play | `POST /servo/play/stop` |

---

## Tips

- Always leave record mode when finished so torque and other features can return.
- Neutral = `512` for tilt and pan.
- Minimum useful motion is usually **2+ frames**; a single frame still plays as a pose.
- If Play fails, confirm phone and bot are on the same Wi‑Fi and the device IP shown on the Actions page is correct.
- Actions are phone-local in v1; reinstalling the app clears saved actions unless you back them up elsewhere.

---

## Related docs

- `rec&play.md` — full design (frames, data format, firmware plan)
- `firmware.md` — BLE Wi‑Fi provisioning
