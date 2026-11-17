# Implementation Scope

This document describes the functionality required by the Lua programs in
`test_cases/` (`01.in` to `06.in`) and whether it can be implemented with ANTLR.

The tests follow the **Lua 5.4 reference manual** section by section (§2 and §3).
The last two tests are stress tests that combine many features in single
expressions. One detail confirms 5.4 specifically: `"10" & 5` is expected to
fail, because 5.4 no longer converts strings to numbers in bitwise operations.

## Required functionality

### 1. Lexer (§3.1)

- **Strings:** escapes `\n \t \0 \x41 \066 \u{2603} \z`, long strings `[[...]]`
  and `[==[...]==]` (the first newline is skipped and escapes are not
  processed), plus short and long comments.
- **Numbers:** decimal integers, hex `0xBEBADA`, floats with exponent
  `314.16e-2`, and hex floats `0X1.921FB54442D18P+1`.
- **Bytes, not characters:** Lua strings are byte sequences. `\u{2603}` must
  become 3 UTF-8 bytes, and the source files contain accented characters. The
  simplest approach is to read and write everything as ISO-8859-1, so each Java
  `char` holds one byte.

### 2. Syntax

- **Statements:** `local` with attributes `<const>` and `<close>`, multiple
  assignment, `do`, `while`, `repeat`/`until`, `if`/`elseif`/`else`, numeric
  `for` (including negative float steps), generic `for`, `goto` and
  `::label::`, `break`, `return`, and empty `;` statements.
- **Function forms:** `function t:m()`, `local function`, varargs `...`, and
  anonymous functions called immediately, like `(function() ... end)()`.
- **Call forms:** `f"str"`, `f{...}`, `obj:method()`, chained calls like
  `x:eye():eye()`, and `;(print or io.write)(...)`.
- **Table constructors:** `[expr]=`, `name=`, positional entries, and both `,`
  and `;` as separators.
- **Operator precedence and associativity:** `-2^2` means `-(2^2)`. `^` and
  `..` are right-associative. `not` binds tighter than `and`, which binds
  tighter than `or`.

### 3. Values and types

- **Types:** `nil`, boolean, number, string, function, table, thread and
  userdata (`io.stdin` only needs `type()` to return `"userdata"`).
- **Integer and float subtypes:** `long` and `double`, where `10 == 10.0` is
  true and `2^3` prints `8.0`.
- **Number printing:** floats print in `%.14g` style with `.0` added when
  needed, e.g. `3.1415926535898`, `3.1416`, `10.0`.
- **Table keys:** a float key with an integer value becomes an integer
  (`a[2.0]` is the same as `a[2]`). Tables, booleans and functions work as
  keys, compared by reference.

### 4. Expressions (§3.4)

- **Arithmetic:** `//` and `%` round toward −∞ (`-10 // 3 = -4`,
  `-10 % 3 = 2`). `/` and `^` always return a float.
- **Bitwise:** `& | ~ ~x << >>`. Shifts are logical, a negative shift goes the
  other way, and shifting by 64 or more gives 0. Floats with an exact integer
  value are accepted (`10.0 & 5.0`); strings are rejected.
- **String-to-number coercion in arithmetic:** includes surrounding spaces
  (`" -10 " + 0` gives the integer `-10`; `"10.5" * 2` gives `21.0`).
- **Comparisons:** NaN rules (`nan == nan` is false), lexicographic string
  `<`, table equality by reference.
- **Logic:** `and`/`or` short-circuit and return one of their operands.
- **Length `#`:** must give the same "border" as real Lua.
  `{10,20,30,nil,50}` gives **5** and `{nil,20,30}` gives **3**, so tables need
  an array part sized by the constructor, like the reference implementation.
- **Multiple results:** a call expands only when it is the last item in a
  list; `(f())` keeps only the first value; this applies in table
  constructors, argument lists, `return`, and `local a,b,c = ...`.
- **Assignment order:** in `i, t[i] = i+1, 20`, the table and key on the left
  are evaluated before anything is assigned.

### 5. Scoping and closures (§2.2)

- Lexical scoping with shadowing (`local x = x`).
- Closures that capture upvalues, with a **fresh local on each loop
  iteration**.
- `_ENV` as the globals table.

### 6. Metatables (§2.4)

- **Arithmetic and bitwise metamethods:** `__add __sub __mul __div __mod __pow
  __unm __idiv __band __bor __bxor __bnot __shl __shr`.
- **Other metamethods:** `__concat __len __eq __lt __le __call __close
  __mode`.
- **`__index` and `__newindex`:** both as functions and as tables, including
  chains where `__index` returns a value that again has a metatable
  (`void.mad.des.rui.mor`).

### 7. Errors (§2.3, §3.3.8)

- `error` with string or table values, `pcall`, and `xpcall` with a handler.
- **`<close>` variables:** they run when the block exits normally *and* when
  an error unwinds it; on error, `__close` receives the error object.
- **Error messages need position info,** like
  `chunkname:50: Este es un error...`. Which chunk name the expected output
  uses (`stdin`? `input`? the file name?) still has to be confirmed against
  the expected outputs.

### 8. Coroutines (§2.6)

- `coroutine.create`, `resume` and `yield`, passing values in both directions,
  plus the `"cannot resume dead coroutine"` error.

### 9. Garbage collection (§2.5)

- A weak table (`__mode="kv"`) must be **empty after
  `collectgarbage("collect")`**. This is the hardest requirement to match
  exactly (see the design notes below).

### 10. Standard library used

`print`, `type`, `tostring`, `pcall`, `xpcall`, `error`, `setmetatable`,
`rawset`, `pairs`, `ipairs`, `collectgarbage`, `load` (from a string),
`coroutine.{create,resume,yield}`, `table.{pack,unpack}`, `io.write`,
`io.stdin`, `math.pi`.

## Feasibility with ANTLR

**Yes, it can be implemented.** ANTLR only builds the lexer and parser. All
the semantics above go into a Java tree-walking interpreter (a Visitor over the
parse tree). Nothing in the tests is beyond that approach.

### Parsing

- The community `grammars-v4` repository has a working Lua grammar
  (`LuaLexer.g4`/`LuaParser.g4`). It can be used as a reference or the
  grammar can be written from scratch.
- ANTLR4's precedence climbing handles left-recursive `exp` rules, with
  `<assoc=right>` for `^` and `..`. The `^` alternative must come before the
  unary operators.
- Long brackets with matching `=` levels are the only tricky lexer rule. Use a
  recursive fragment (as grammars-v4 does) or a small lexer action.
- `load(string)` just runs the same parser at runtime.

### Runtime design notes (Java)

| Feature | Approach |
|---|---|
| `return` / `break` / `goto` | Control-flow exceptions or signal values. For `goto`, a block runs its statements by index and jumps to the label's index. |
| `error` / `pcall` | A `LuaError extends RuntimeException` carrying a `LuaValue`. Record the line from `ctx.getStart().getLine()` for the message prefix. |
| `<close>` | `try/finally` in block execution, passing the error object if one is in flight. |
| Coroutines | One thread per coroutine with a synchronous handoff (virtual threads are ideal on Java 21+). |
| Closures | Environment frames linked to their parent, with a new frame per loop iteration. |
| Weak tables | Either back them with `WeakReference`/`WeakHashMap` and call `System.gc()` in `collectgarbage` (usually works on HotSpot but isn't guaranteed), or write a small mark phase from the interpreter's roots (globals and live frames) that clears unreachable weak entries. The second option is deterministic. |
| `#` border | An array part (`ArrayList`) plus a hash part (`HashMap`), with constructor entries going into the array part. |

### Main risks

The riskiest areas for matching the expected output exactly are:

1. Number formatting (`%.14g` and the `.0` suffix).
2. Error-message prefixes (chunk name and line number).
3. The `#` border on tables with holes.
4. The weak-table/GC test.

Everything else is straightforward.
