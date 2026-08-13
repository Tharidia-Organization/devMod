#!/bin/zsh
set -euo pipefail

LABEL="com.codex.warmmic"
UID_NUM="$(id -u)"
DEFAULT_INPUT_NAME="${DEFAULT_INPUT_NAME:-Yeti Stereo Microphone}"

launchctl bootout "gui/${UID_NUM}/${LABEL}" >/dev/null 2>&1 || true
echo "Stopped warm mic chain (${LABEL})."

if command -v SwitchAudioSource >/dev/null 2>&1; then
  SwitchAudioSource -s "$DEFAULT_INPUT_NAME" -t input >/dev/null || true
fi

echo "Input restored to: $DEFAULT_INPUT_NAME"
