#!/bin/bash
# Fresh stage 1 from the template (opened with "$VP" open: the old-format template needs conversion, which revert would decline).
HERE="$(cd "$(dirname "$0")" && pwd)"
VP="$HERE/../../bin/vp"
WS="${LAB2_WORKSPACE:-$HOME/Documents/урфу/учеба/3 курс 1 сем/5 сем/Архитектура ПО/Лаба 2/workspace}"
cd "$WS"
cp "$WS"/myProject_original_template.vpp "$WS"/myProject.vpp
rm -f /tmp/vpopen.out; ("$VP" open "$WS"/myProject.vpp > /tmp/vpopen.out 2>&1 &); sleep 2
for i in $(seq 1 20); do d=$("$VP" dialogs 2>/dev/null); echo "$d" | grep -q "Do you want to save" && "$VP" click No "Do you want to save" >/dev/null; grep -q ok /tmp/vpopen.out 2>/dev/null && break; sleep 1; done; cat /tmp/vpopen.out; "$VP" status
: > "$WS"/vp_lab2.log
"$VP" run "$HERE"/S1.java 2>&1 | grep -E 'error|\.java:[0-9]+|^(FAIL|WARN)' | head
grep -E '^(FAIL|WARN)' "$WS"/vp_lab2.log | head
"$VP" save >/dev/null; "$VP" revert >/dev/null
cp "$WS"/myProject.vpp "$WS"/myProject_stage1_done.vpp
echo rebuilt1
