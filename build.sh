#!/usr/bin/env bash
# Generates the lexer/parser from grammar/*.g4 into gen/, then compiles
# gen/ and src/ into out/.
set -euo pipefail
cd "$(dirname "$0")"

CP=lib/antlr-4.13.2-complete.jar
[ -f "$CP" ] || { echo "missing $CP (https://www.antlr.org/download/antlr-4.13.2-complete.jar)" >&2; exit 1; }

rm -rf gen out
mkdir gen out

echo "==> Generating Java from grammars"
# Lexer first: the parser's tokenVocab needs LuaLexer.tokens.
(cd grammar && java -jar "../$CP" -Dlanguage=Java -visitor -no-listener -o ../gen LuaLexer.g4 LuaParser.g4)

echo "==> Compiling"
javac -cp "$CP" -d out gen/*.java src/*.java

echo "Build OK"
