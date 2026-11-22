




























print("\n--- 3.4.10 Function Calls ---")
local function call_target(...)
    return ...
end

print("Llamada estandar:", call_target(1, 2, 3))
print("Llamada sugar con Literal String:", call_target"Cadena Directa")
print("Llamada sugar con Tabla:", type(call_target{ a = 1 }))

local method_test = { val = 500 }
function method_test:action(extra)
    return self.val + extra
end

print("Llamada a metodo con colon syntax (v:name):", method_test:action(100))


