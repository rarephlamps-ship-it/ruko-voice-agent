# Termux Acoustic Detector

Local Android/Termux software for a large external microphone or the phone microphone. It analyses short recordings and flags sound patterns consistent with:

- human speech activity (no transcription and no speaker identification)
- footsteps or impact-like low-frequency impulses
- sustained harmonic drone/multirotor propeller noise

The classifier is heuristic. Wind, traffic, fans, music and room reflections can cause false detections. Use it only where recording is lawful and appropriate.

## Requirements

1. Install **Termux** and the matching **Termux:API** Android app from the same trusted distribution source.
2. Give Termux:API microphone permission in Android settings.
3. In Termux, select a working package mirror if package installation fails:

```bash
termux-change-repo
```

Choose **Main repository**, then a current official/available mirror.

## Install

```bash
pkg update -y
pkg install -y git
git clone https://github.com/rarephlamps-ship-it/ruko-voice-agent.git
cd ruko-voice-agent/termux-acoustic-detector
chmod +x install.sh
./install.sh
source ~/.bashrc
```

Start:

```bash
termux-acoustic-detector
```

Stop with `Ctrl+C`.

## Test microphone access

```bash
termux-microphone-record -f "$HOME/test.m4a" -l 5
sleep 6
termux-microphone-record -q
ls -lh "$HOME/test.m4a"
```

## Configuration

Edit:

```bash
nano ~/.config/termux-acoustic-detector/config.json
```

Important settings:

- `minimum_rms`: lower means more sensitive and more false alarms.
- `software_gain`: amplifies both useful audio and noise; it does not create physical microphone range.
- `speech_threshold`, `footstep_threshold`, `drone_threshold`: higher means fewer alarms.
- `save_event_audio`: saves only classified event clips.
- `retention_days`: automatically removes old event clips; `0` disables deletion.

Logs:

```bash
tail -n 30 ~/.local/share/termux-acoustic-detector/events.csv
```

Saved event audio:

```bash
ls -lh ~/.local/share/termux-acoustic-detector/events/
```

## External microphone

For long-distance pickup, use a directional shotgun/parabolic microphone connected through a compatible USB audio interface with physical gain control. Android—not this script—selects the active input. Verify the external microphone first with an ordinary Android recorder.

## Limitations

This is an acoustic pattern detector, not forensic identification. It cannot reliably determine who is speaking, what is being said, the exact source distance, or whether every harmonic motor sound is a drone.
