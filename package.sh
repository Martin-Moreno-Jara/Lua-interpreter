#!/usr/bin/env bash
# Builds dist/lua-interpreter.zip for UNCode:
#   src/  generated parser (gen/*.java) + our sources, including Main.java
#   lib/  ANTLR runtime jar
# Then unzips a copy, compiles it as Java 8 and runs every test case through
# stdin to check that the zip works on its own.
set -euo pipefail
cd "$(dirname "$0")"

RUNTIME=lib/antlr4-runtime-4.13.2.jar
ZIP=dist/lua-interpreter.zip
# Classpath separator: ';' on Windows (Git Bash/MSYS/Cygwin), ':' elsewhere.
case "$(uname -s)" in MINGW*|MSYS*|CYGWIN*) SEP=";" ;; *) SEP=":" ;; esac
[ -f "$RUNTIME" ] || {
  echo "missing $RUNTIME (https://repo1.maven.org/maven2/org/antlr/antlr4-runtime/4.13.2/antlr4-runtime-4.13.2.jar)" >&2
  exit 1
}

./build.sh

echo "==> Packaging $ZIP"
rm -rf dist
mkdir -p dist/stage/src dist/stage/lib
# Always remove temp dirs; on failure also remove the zip so a broken one is never left behind.
trap 'status=$?; rm -rf dist/stage dist/check; [ $status -eq 0 ] || rm -f "$ZIP"' EXIT
cp gen/*.java src/*.java dist/stage/src/
cp "$RUNTIME" dist/stage/lib/
# Zip from inside stage/ so src/ and lib/ sit at the top level of the zip.
# Use zip if available; otherwise the JDK's jar tool (-M: no manifest, plain zip).
if command -v zip >/dev/null 2>&1; then
  (cd dist/stage && zip -qr ../lua-interpreter.zip src lib)
else
  # jar is often not on PATH (e.g. Oracle JDK on Windows): find it under java.home.
  JAR=$(command -v jar || true)
  if [ -z "$JAR" ]; then
    JHOME=$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java.home = //p' | tr -d '')
    JAR="$JHOME/bin/jar"
  fi
  (cd dist/stage && "$JAR" cfM ../lua-interpreter.zip src lib)
fi

echo "==> Verifying the zip (Java 8 compile + test cases via stdin)"
CHECK=dist/check
mkdir -p "$CHECK/classes"
unzip -q "$ZIP" -d "$CHECK"
javac --release 8 -Xlint:-options -encoding UTF-8 -cp "$CHECK/lib/*" -d "$CHECK/classes" "$CHECK"/src/*.java

fail=0
for f in test_cases/*.in; do
  # Through stdin the chunk is named "stdin" (as in lua5.4), so error prefixes
  # like "test_cases/01.in:49:" in the .expected become "stdin:49:" here.
  # 2>&1 so any stderr output (e.g. an ANTLR version warning) also counts as a failure.
  if d=$(java -cp "$CHECK/classes${SEP}$CHECK/lib/*" Main <"$f" 2>&1 \
         | diff - <(sed "s|$f:|stdin:|g" "${f%.in}.expected")); then
    echo "PASS  $f"
  else
    echo "FAIL  $f"
    sed 's/^/        /' <<<"$d"
    fail=$((fail + 1))
  fi
done

echo
unzip -l "$ZIP"
[ $fail -eq 0 ] && echo "Zip OK: $ZIP" || {
  echo "$fail test case(s) failed" >&2
  exit 1
}
