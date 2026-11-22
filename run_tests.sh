#!/usr/bin/env bash
# Parses every test_cases/*.in and shows any lexical or syntax errors.
# Usage: ./run_tests.sh [-b]   (-b rebuilds first)
cd "$(dirname "$0")"

[ "${1:-}" = "-b" ] && { ./build.sh || exit 1; }
[ -d out ] || { echo "out/ not found: run ./build.sh first" >&2; exit 1; }

CP=lib/antlr-4.13.2-complete.jar
fail=0
for f in test_cases/*.in; do
    # stdout: error count; stderr: ANTLR's "line:col message" lines
    out=$(java -cp "out:$CP" Main "$f" 2>&1)
    status=$?
    if [ $status -eq 0 ] && grep -q '^lexical errors: 0$' <<<"$out" && grep -q '^syntax errors: 0$' <<<"$out"; then
        echo "PASS  $f"
    else
        echo "FAIL  $f"
        sed 's/^/        /' <<<"$out"
        fail=$((fail + 1))
    fi
done

echo
[ $fail -eq 0 ] && echo "All test cases parsed without errors." || echo "$fail test case(s) failed."
exit $((fail > 0))
