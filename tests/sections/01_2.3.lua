













































print("\n--- 2.3 Error Handling ---")
local status, err = pcall(function()
    error("Este es un error simulado para pcall")
end)
print("Resultado de pcall (Debe ser false):", status)
print("Mensaje atrapado por pcall:", err)

local status2, err2 = xpcall(function()
    error({ code = 502, msg = "Objeto de error no-string" })
end, function(err_obj)
    return "TRACEBACK GENERADO -> " .. tostring(err_obj.msg)
end)
print("Resultado de xpcall (Debe ser false):", status2)
print("Mensaje atrapado por xpcall:", err2)

