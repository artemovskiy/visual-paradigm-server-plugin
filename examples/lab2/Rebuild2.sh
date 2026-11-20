#!/bin/bash
# Fresh stage 2 from the stage-1 backup, then save + reopen (verification state).
HERE="$(cd "$(dirname "$0")" && pwd)"
VP="$HERE/../../bin/vp"
WS="${LAB2_WORKSPACE:-$HOME/Documents/урфу/учеба/3 курс 1 сем/5 сем/Архитектура ПО/Лаба 2/workspace}"
cd "$WS"
cp "$WS"/myProject_stage1_done.vpp "$WS"/myProject.vpp
"$VP" revert >/dev/null
"$VP" run "$HERE"/All2.java 2>&1 | grep -E '^(FAIL|WARN)|rror'
grep -E '^(FAIL|WARN)' "$WS"/vp_lab2.log
"$VP" save >/dev/null; "$VP" revert >/dev/null
echo rebuilt
