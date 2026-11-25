#!/usr/bin/env bash
# Runs every test_cases/NN.in and compares the output (stdout + stderr)
# byte for byte against test_cases/NN.expected.
# Usage: ./run_tests.sh [-b]   (-b rebuilds first)
cd "$(dirname "$0")"

[ "${1:-}" = "-b" ] && { ./build.sh || exit 1; }
[ -d out ] || { echo "out/ not found: run ./build.sh first" >&2; exit 1; }

CP=lib/antlr-4.13.2-complete.jar
# Classpath separator: ';' on Windows (Git Bash/MSYS/Cygwin), ':' elsewhere.
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=";" ;; *) SEP=":" ;; esac
total=0
fail=0
for f in test_cases/*.in; do
    expected="${f%.in}.expected"
    total=$((total + 1))
    if [ ! -f "$expected" ]; then
        echo "FAIL  $f  (missing $expected)"
        fail=$((fail + 1))
        continue
    fi
    # Run from the repo root with the relative path so error prefixes
    # (e.g. "test_cases/01.in:49:") match what lua5.4 printed.
    if d=$(java -cp "out${SEP}$CP" Main "$f" 2>&1 | diff - "$expected"); then
        echo "PASS  $f"
    else
        echo "FAIL  $f"
        sed 's/^/        /' <<<"$d"
        fail=$((fail + 1))
    fi
done

echo
echo "$((total - fail))/$total test case(s) passed."
exit $((fail > 0))
