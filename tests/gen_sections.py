#!/usr/bin/env python3
"""Genera tests/sections/<id>.lua y <id>.expected a partir de tests/sections.txt.

Cada fragmento conserva los números de línea del caso original (las líneas no
incluidas quedan en blanco). El .expected se obtiene ejecutando lua5.4 real
desde la raíz del repo, con la misma ruta relativa que usará la prueba, para
que los prefijos de error "tests/sections/<id>.lua:N:" coincidan.

Al final verifica que la salida de las secciones de cada caso, concatenada,
sea igual a test_cases/<caso>.expected (cambiando el nombre del archivo en
los mensajes de error). Así se comprueba que el corte en secciones es fiel.

Solo hace falta ejecutarlo si cambia tests/sections.txt o algún caso.
Requiere lua5.4.
"""
import os
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MANIFEST = os.path.join("tests", "sections.txt")
OUT_DIR = os.path.join("tests", "sections")
ENC = "latin-1"  # un char por byte, igual que el intérprete


def parse_manifest():
    sections = []
    with open(MANIFEST, encoding="utf-8") as f:
        for raw in f:
            line = raw.strip()
            if not line or line.startswith("#"):
                continue
            parts = line.split(None, 4)
            sid, case, owners, ranges, title = parts
            sections.append((sid, case, owners, ranges, title))
    return sections


def parse_ranges(spec, last):
    lines = set()
    for part in spec.split(","):
        a, _, b = part.partition("-")
        a = last if a == "end" else int(a)
        b = a if not b else (last if b == "end" else int(b))
        lines.update(range(a, b + 1))
    return lines


def run_lua(path):
    p = subprocess.run(["lua5.4", path], stdout=subprocess.PIPE,
                       stderr=subprocess.STDOUT, timeout=30)
    return p.stdout


def main():
    os.chdir(ROOT)
    os.makedirs(OUT_DIR, exist_ok=True)
    sections = parse_manifest()
    by_case = {}
    for sid, case, owners, ranges, title in sections:
        src = os.path.join("test_cases", case + ".in")
        with open(src, "rb") as f:
            src_lines = f.read().decode(ENC).split("\n")
        keep = parse_ranges(ranges, len(src_lines))
        top = max(keep)
        body = [src_lines[i - 1] if i in keep else "" for i in range(1, top + 1)]
        lua_path = os.path.join(OUT_DIR, sid + ".lua")
        with open(lua_path, "wb") as f:
            f.write(("\n".join(body) + "\n").encode(ENC))
        out = run_lua(lua_path)
        with open(os.path.join(OUT_DIR, sid + ".expected"), "wb") as f:
            f.write(out)
        by_case.setdefault(case, []).append((lua_path, out))
        print("generado", lua_path)

    ok = True
    for case, parts in sorted(by_case.items()):
        full_path = os.path.join("test_cases", case + ".in")
        joined = b"".join(out.replace(p.encode(), full_path.encode()) for p, out in parts)
        with open(os.path.join("test_cases", case + ".expected"), "rb") as f:
            expected = f.read()
        if joined == expected:
            print("OK   las secciones del caso %s reproducen %s.expected" % (case, case))
        else:
            ok = False
            print("ERR  las secciones del caso %s NO reproducen %s.expected" % (case, case))
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
