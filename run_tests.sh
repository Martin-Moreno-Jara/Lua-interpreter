#!/usr/bin/env bash
<<<<<<< HEAD
# Runs every test_cases/NN.in and compares the output (stdout + stderr)
# byte for byte against test_cases/NN.expected.
=======
# Parses every test_cases/*.in and shows any lexical or syntax errors.
>>>>>>> grammar-fix
# Usage: ./run_tests.sh [-b]   (-b rebuilds first)
cd "$(dirname "$0")"

[ "${1:-}" = "-b" ] && { ./build.sh || exit 1; }
[ -d out ] || { echo "out/ not found: run ./build.sh first" >&2; exit 1; }

CP=lib/antlr-4.13.2-complete.jar
total=0
fail=0
for f in test_cases/*.in; do
<<<<<<< HEAD
    expected="${f%.in}.expected"
    total=$((total + 1))
    if [ ! -f "$expected" ]; then
        echo "FAIL  $f  (missing $expected)"
        fail=$((fail + 1))
        continue
    fi
    # Run from the repo root with the relative path so error prefixes
    # (e.g. "test_cases/01.in:49:") match what lua5.4 printed.
    if d=$(java -cp "out:$CP" Main "$f" 2>&1 | diff - "$expected"); then
=======
    # stdout: error count; stderr: ANTLR's "line:col message" lines
    out=$(java -cp "out:$CP" Main "$f" 2>&1)
    status=$?
    if [ $status -eq 0 ] && grep -q '^lexical errors: 0$' <<<"$out" && grep -q '^syntax errors: 0$' <<<"$out"; then
>>>>>>> grammar-fix
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
