#!/usr/bin/env bash
# Pruebas JUnit 5: cada caso completo y cada sección de tests/sections.txt,
# comparando la salida del intérprete con la de lua5.4.
#
# Uso: ./run_unit_tests.sh [-b] [-c CASO] [-o DUEÑO]
#   -b        recompila el intérprete antes (./build.sh)
#   -c 03     solo el caso 03 (completo y sus secciones)
#   -o B      solo las secciones cuyo dueño incluye a B (sin casos completos)
set -euo pipefail
cd "$(dirname "$0")"

JUNIT=lib/junit-platform-console-standalone-1.11.4.jar
ANTLR=lib/antlr-4.13.2-complete.jar
[ -f "$JUNIT" ] || {
  echo "falta $JUNIT. Descárgalo con:" >&2
  echo "  curl -fsSL -o $JUNIT https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar" >&2
  exit 1
}

props=()
build=0
while getopts "bc:o:" opt; do
  case $opt in
    b) build=1 ;;
    c) props+=("-Dlua.case=$OPTARG") ;;
    o) props+=("-Dlua.owner=$OPTARG") ;;
    *) sed -n '5,8p' "$0" >&2; exit 2 ;;
  esac
done

if [ $build -eq 1 ] || [ ! -d out ]; then ./build.sh; fi

rm -rf out-test
mkdir out-test
# NumberFormatTest y LuaTableTest llaman a clases de src/ directamente, por eso necesitan out/
javac -encoding UTF-8 -cp "$JUNIT:out" -d out-test tests/LuaTestCasesTest.java tests/NumberFormatTest.java tests/LuaTableTest.java

java "${props[@]}" -jar "$JUNIT" execute \
  --class-path "out-test:out" \
  --select-class LuaTestCasesTest \
  --select-class NumberFormatTest \
  --select-class LuaTableTest \
  --details=tree --disable-banner
