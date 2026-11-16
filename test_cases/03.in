print("--- 3.4.1 Arithmetic Operators ---")
local int_val = 10
local float_val = 3.14
print("Suma (Int + Int = Int):", int_val + 5)
print("Suma (Int + Float = Float):", int_val + float_val)
print("Division Float (/):", int_val / 3)
print("Division Entera (//):", int_val // 3)
print("Division Entera Negativa (hacia -infinito):", -10 // 3)
print("Modulo (%):", int_val % 3)
print("Modulo Negativo:", -10 % 3)
print("Exponenciacion (^, siempre float):", 2 ^ 3)
print("Menos Unario:", -int_val)

print("\n--- 3.4.2 Bitwise Operators ---")
local b1 = 0xA
local b2 = 0x3
print("Bitwise AND (&):", b1 & b2)
print("Bitwise OR (|):", b1 | b2)
print("Bitwise XOR (~):", b1 ~ b2)
print("Bitwise NOT (~ unario):", ~b1)
print("Shift Right (>>):", b1 >> 1)
print("Shift Left (<<):", b1 << 2)
print("Shift Negativo (equivale a direccion opuesta):", b1 << -1)
print("Shift Mayor a bits (debe ser 0):", b1 >> 100)

print("\n--- 3.4.3 Coercions and Conversions ---")
print("Coercion aritmetica (String a Numero):", "10" + 5)
print("Coercion aritmetica Float a Numero:", "10.5" * 2)
print("Bitwise de Float representable como Int:", 10.0 & 5.0)

local status_bitwise, err_bitwise = pcall(function()
    return "10" & 5
end)
print("Fallo esperado en Bitwise String a Int:", not status_bitwise)

print("\n--- 3.4.4 Relational Operators ---")
print("Igualdad (Mismo tipo y valor):", 10 == 10.0)
print("Igualdad (Fallo por tipo distinto):", "10" == 10)
print("Desigualdad (~=):", 10 ~= 20)
print("Comparacion de Strings (Lexicografica):", "apple" < "banana")

local t1 = {val = 1}
local t2 = {val = 1}
print("Igualdad de Tablas (Referencia, debe ser false):", t1 == t2)
print("Igualdad de Tablas (Misma Referencia, true):", t1 == t1)

local nan = 0/0
print("NaN es menor que NaN (false):", nan < nan)
print("NaN es igual a NaN (false):", nan == nan)
print("NaN es mayor o igual a NaN (false):", nan >= nan)

print("\n--- 3.4.5 Logical Operators ---")
print("Operador AND (true and true):", true and "resultado")
print("Operador AND (false and true):", false and "resultado")
print("Operador AND (nil and true):", nil and 10)
print("Operador OR (true or false):", 10 or 20)
print("Operador OR (false or true):", false or "fallback")
print("Cero es true en Lua:", 0 and "cero es true")
print("String vacio es true:", "" and "vacio es true")

local side_effect = false
local function logical_test()
    side_effect = true
    return true
end
print("Short-circuit OR:", true or logical_test())
print("Efecto secundario evitado por short-circuit:", not side_effect)

print("\n--- 3.4.6 Concatenation ---")
print("Concatenacion String-String:", "Hola " .. "Mundo")
print("Concatenacion Number-Number:", 10 .. 20)
print("Concatenacion String-Number:", "Valor: " .. 99)

print("\n--- 3.4.7 The Length Operator ---")
print("Longitud de String (#):", #"Hola Mundo")

local seq = {10, 20, 30, 40, 50}
print("Longitud de Secuencia Pura:", #seq)

local non_seq_hole = {10, 20, 30, nil, 50}
print("Longitud con Agujero (Border puede ser 3 o 5):", #non_seq_hole)

local non_seq_start = {nil, 20, 30}
print("Longitud con Agujero Inicial (Border puede ser 0 o 3):", #non_seq_start)

local seq_empty = {}
print("Longitud de Tabla Vacia:", #seq_empty)

print("\n--- 3.4.8 Precedence ---")
print("Precedencia Aritmetica (10 + 2 * 5):", 10 + 2 * 5)
print("Precedencia Exponenciacion y Unario (-2^2 es -(2^2)):", -2^2)
print("Asociatividad Derecha de Exponenciacion (2^3^2):", 2^3^2)
print("Asociatividad Derecha Concatenacion:", "a" .. "b" .. "c")
print("Precedencia Logica vs Relacional:", not false and 10 > 5 or false)