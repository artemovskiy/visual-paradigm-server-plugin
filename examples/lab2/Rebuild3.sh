#!/bin/bash
# Fresh stage 3 on top of the stage-2 backup, then save + reopen.
HERE="$(cd "$(dirname "$0")" && pwd)"
VP="$HERE/../../bin/vp"
WS="${LAB2_WORKSPACE:-$HOME/Documents/урфу/учеба/3 курс 1 сем/5 сем/Архитектура ПО/Лаба 2/workspace}"
cd "$WS"
cp "$WS"/myProject_stage2_done.vpp "$WS"/myProject.vpp
"$VP" revert >/dev/null
"$VP" run "$HERE"/S3.java 2>&1 | grep -E '^(FAIL|WARN)|error|\.java:[0-9]+' | head -20
"$VP" save >/dev/null; "$VP" revert >/dev/null
echo rebuilt3
