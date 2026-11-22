


















































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

