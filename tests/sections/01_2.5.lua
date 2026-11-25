











































































































print("\n--- 2.5 Garbage Collection ---")
local weak_table = setmetatable({}, { __mode = "kv" })
local temp_key = { key = "temporal" }
local temp_val = { val = "datos" }
weak_table[temp_key] = temp_val

print("¿La tabla débil tiene elementos antes del GC?", weak_table[temp_key] ~= nil)
temp_key = nil
collectgarbage("collect")

local has_elements = false
for k, v in pairs(weak_table) do has_elements = true end
print("¿La tabla débil tiene elementos después del GC?", has_elements)

local finalizer_executed = false
local FinalizerClass = {
    __close = function(obj)
        finalizer_executed = true
        print("  -> Metamétodo __close invocado al salir del bloque")
    end
}

do
    local file_sim <close> = setmetatable({}, FinalizerClass)
end
print("¿Se ejecutó <close> automáticamente al cerrar el 'do...end'?", finalizer_executed)

