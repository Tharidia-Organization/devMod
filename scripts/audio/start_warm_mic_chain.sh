#!/bin/zsh
set -euo pipefail

FFMPEG_BIN="/opt/homebrew/bin/ffmpeg"
LABEL="com.codex.warmmic"
UID_NUM="$(id -u)"
PLIST="$HOME/Library/LaunchAgents/${LABEL}.plist"
LOG_FILE="/tmp/warm_mic_chain.log"
INPUT_DEVICE_INDEX="${INPUT_DEVICE_INDEX:-1}"   # 1 = Yeti Stereo Microphone
OUTPUT_DEVICE_NAME="${OUTPUT_DEVICE_NAME:-BlackHole 2ch}"

if ! command -v "$FFMPEG_BIN" >/dev/null 2>&1; then
  echo "ffmpeg not found at $FFMPEG_BIN"
  exit 1
fi

cat > "$PLIST" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>Label</key>
  <string>${LABEL}</string>
  <key>ProgramArguments</key>
  <array>
    <string>${FFMPEG_BIN}</string>
    <string>-hide_banner</string>
    <string>-f</string><string>avfoundation</string>
    <string>-thread_queue_size</string><string>512</string>
    <string>-i</string><string>:${INPUT_DEVICE_INDEX}</string>
    <string>-af</string><string>highpass=f=70,equalizer=f=160:t=q:w=1.0:g=4,equalizer=f=3200:t=q:w=1.2:g=-2,deesser=i=0.18:m=0.5:f=0.5:s=o,acompressor=threshold=0.18:ratio=2.8:attack=8:release=180:makeup=2,alimiter=limit=0.92,volume=1.4</string>
    <string>-ar</string><string>48000</string>
    <string>-ac</string><string>2</string>
    <string>-f</string><string>audiotoolbox</string>
    <string>${OUTPUT_DEVICE_NAME}</string>
  </array>
  <key>RunAtLoad</key><true/>
  <key>KeepAlive</key><true/>
  <key>StandardOutPath</key><string>${LOG_FILE}</string>
  <key>StandardErrorPath</key><string>${LOG_FILE}</string>
</dict>
</plist>
PLIST

launchctl bootout "gui/${UID_NUM}/${LABEL}" >/dev/null 2>&1 || true
launchctl bootstrap "gui/${UID_NUM}" "$PLIST"
launchctl kickstart -k "gui/${UID_NUM}/${LABEL}"

if command -v SwitchAudioSource >/dev/null 2>&1; then
  SwitchAudioSource -s "$OUTPUT_DEVICE_NAME" -t input >/dev/null
fi

echo "Warm mic chain started via launchd (${LABEL})."
echo "Input is now routed to: $OUTPUT_DEVICE_NAME"
echo "Log: $LOG_FILE"
