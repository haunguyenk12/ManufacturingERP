#!/usr/bin/env bash
# Chạy Maven offline, ghi log đầy đủ ra file, chỉ in phần tóm tắt (tiết kiệm token).
#
#   bash scripts/mvn-summary.sh test -Dtest=OneClass
#   bash scripts/mvn-summary.sh clean verify
#
# Log đầy đủ nằm ngoài target/ để `mvn clean` không xoá nó; đọc bằng Grep/Read khi cần.
# Đừng sửa file nguồn trong lúc script chạy (VS Code Java LS sẽ ghi đè target/classes).
LOG="${MVN_LOG:-${TMPDIR:-/tmp}/mvn-last.log}"
mvn -o "$@" > "$LOG" 2>&1
rc=$?

echo "exit=$rc  log=$LOG"
echo "--- totals ---"
grep -E 'Tests run:.*Skipped: [0-9]+$' "$LOG" | sed -E 's/^\[[A-Z]+\] //' | tail -4
echo "--- build ---"
grep -E 'BUILD (SUCCESS|FAILURE)|COMPILATION ERROR|Unable to find main class' "$LOG" | sed -E 's/^\[[A-Z]+\] //' | head -5
echo "--- failed tests / compile errors ---"
{
  grep -E -A30 '^\[ERROR\] (Failures|Errors|Tests run:.*FAIL)' "$LOG" | grep -E '^\[ERROR\]' | sed -E 's/^\[ERROR\] +//'
  grep -E '^\[ERROR\] .*\.java:\[[0-9]+,[0-9]+\]' "$LOG" | sed -E 's/^\[ERROR\] +//'
} | awk '!seen[$0]++' | head -40
exit $rc
