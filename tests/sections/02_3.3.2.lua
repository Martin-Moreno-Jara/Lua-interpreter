


















































































































print("\n--- 3.3.2 y Extensiones: Chunks y global ---")
local chunk_code = [[
    return _ENV.math.pi
]]
local chunk_func = load(chunk_code)
if chunk_func then
    print("Chunk cargado dinamicamente (math.pi):", chunk_func())
end

local custom_global_chunk = [[
    global_test_var = "Prueba de variable global implicita"
    return global_test_var
]]
local custom_func = load(custom_global_chunk)
if custom_func then
    print("Resultado de ejecucion de chunk global:", custom_func())
end
