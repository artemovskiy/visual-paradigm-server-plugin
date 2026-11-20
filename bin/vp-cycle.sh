#!/bin/bash
# Restart VP without saving, reopen myProject.vpp, run the given plugin buttons, screenshot, give focus back to Claude.
# Usage: vp-cycle.sh [restart] btn... ; btn = 1 | 1b | 1c | 2 | 2b | inspect
W="${VP_WORKSPACE:?set VP_WORKSPACE to the folder with myProject.vpp and vp_lab2.log}"
L="$W/vp_lab2.log"; C=/opt/homebrew/bin/cliclick
SHOT="${SHOT:-/tmp/vp_shot.png}"
P=JavaApplicationStub
ax() { osascript -e "tell application \"System Events\" to tell process \"$P\" to $1" 2>/dev/null; }
front() { osascript -e "tell application \"System Events\" to set frontmost of process \"$P\" to true"; sleep 0.5; }
back() { osascript -e 'tell application "Claude" to activate'; }
wins() { ax 'get name of every window'; }

if [ "$1" = restart ]; then
  shift
  if pgrep -f $P >/dev/null; then
    front; osascript -e "tell application \"System Events\" to tell process \"$P\" to key code 12 using command down"
    for i in $(seq 1 10); do sleep 1; wins | grep -q 'Visual Paradigm CE' && break; done
    ax 'click button "No" of window "Visual Paradigm CE"'
    for i in $(seq 1 60); do pgrep -f $P >/dev/null || break; sleep 1; done
  fi
  open -a "Visual Paradigm CE" "$W/myProject.vpp"
  for i in $(seq 1 90); do wins | grep -q 'MyProject' && break; sleep 1; done
  for i in $(seq 1 20); do
    w="$(wins)"
    echo "$w" | grep -q Activate && ax 'click button "Remind me later" of (first window whose name starts with "Activate")'
    echo "$w" | grep -q "What's New" && ax "click (first button of window \"What's New\" whose subrole is \"AXCloseButton\")"
    [ "$(wins)" = "MyProject - Visual Paradigm Community Edition (not for commercial use)" ] && [ $i -gt 6 ] && break
    sleep 1
  done
  front; $C c:909,73; sleep 1
fi

for btn in "$@"; do
  case $btn in
    1) xy=88,99;; 2) xy=266,99;; 1b) xy=88,127;; 2b) xy=252,127;; 1c) xy=96,155;; inspect) xy=232,155;;
  esac
  n=$(wc -l < "$L" 2>/dev/null || echo 0)
  front; $C c:909,73 w:500 c:$xy
  for i in $(seq 1 90); do tail -n +$((n+1)) "$L" 2>/dev/null | grep -qE '^=====|^Inspect|Dump' && break; sleep 1; done
  sleep 1
done
front; screencapture -x "$SHOT"; sips -Z 1470 "$SHOT" --out "$SHOT" >/dev/null
back
grep -E '^=====|FAIL|WARN|seq done' "$L" | tail -15
