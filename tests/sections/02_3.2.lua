















print("\n--- 3.2 y 3.3.3 Variables y Asignacion Multiple ---")
local a, b, c = 1, 2, 3
print("Original:", a, b, c)
a, b, c = c, a, b
print("Permutado (a,b,c = c,a,b):", a, b, c)

local t = {}
local i = 1
i, t[i] = i + 1, 20
print("Evaluacion estricta de indices:")
print("i:", i, "t[1]:", t[1], "t[2]:", t[2])

