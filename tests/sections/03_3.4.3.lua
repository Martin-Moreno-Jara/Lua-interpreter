
























print("\n--- 3.4.3 Coercions and Conversions ---")
print("Coercion aritmetica (String a Numero):", "10" + 5)
print("Coercion aritmetica Float a Numero:", "10.5" * 2)
print("Bitwise de Float representable como Int:", 10.0 & 5.0)

local status_bitwise, err_bitwise = pcall(function()
    return "10" & 5
end)
print("Fallo esperado en Bitwise String a Int:", not status_bitwise)

