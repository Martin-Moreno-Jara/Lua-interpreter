


































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

