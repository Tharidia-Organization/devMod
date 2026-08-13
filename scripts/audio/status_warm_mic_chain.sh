#!/bin/zsh
set -euo pipefail

LABEL="com.codex.warmmic"
UID_NUM="$(id -u)"
LOG_FILE="/tmp/warm_mic_chain.log"

if launchctl print "gui/${UID_NUM}/${LABEL}" >/tmp/warm_mic_launchctl_status.txt 2>/dev/null; then
  STATE_LINE="$(rg -n 'state =|pid =' -S /tmp/warm_mic_launchctl_status.txt | sed -n '1,2p' || true)"
  echo "Warm mic chain: RUNNING (${LABEL})"
  [[ -n "$STATE_LINE" ]] && echo "$STATE_LINE"
else
  echo "Warm mic chain: NOT RUNNING"
fi

if command -v SwitchAudioSource >/dev/null 2>&1; then
  CURRENT_INPUT="$(SwitchAudioSource -c -t input 2>/dev/null || true)"
  echo "Current input device: ${CURRENT_INPUT:-unknown}"
fi

if [[ -f "$LOG_FILE" ]]; then
  echo "Log tail:"
  tail -n 15 "$LOG_FILE"
fi
