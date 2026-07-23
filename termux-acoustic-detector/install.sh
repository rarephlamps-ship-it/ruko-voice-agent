#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

pkg update -y
pkg upgrade -y
pkg install -y python ffmpeg termux-api

mkdir -p "$HOME/.local/bin" "$HOME/.config/termux-acoustic-detector"
cp detector.py "$HOME/.local/bin/termux-acoustic-detector"
cp config.json "$HOME/.config/termux-acoustic-detector/config.json"
chmod +x "$HOME/.local/bin/termux-acoustic-detector"

if ! grep -q 'HOME/.local/bin' "$HOME/.bashrc" 2>/dev/null; then
  echo 'export PATH="$HOME/.local/bin:$PATH"' >> "$HOME/.bashrc"
fi

printf '\nInstalled. Ensure the separate Termux:API Android app is installed and microphone permission is enabled.\n'
printf 'Start with: termux-acoustic-detector\n'
