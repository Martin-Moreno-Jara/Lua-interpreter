print("--- 3.4.9 Table Constructors ---")
local f_key = function(x) return x * 2 end
local complex_table = { 
    [f_key(10)] = "Clave Dinamica", 
    "Posicion 1", 
    "Posicion 2"; 
    x = 100, 
    [30] = 23; 
    "Posicion 3"
}

print("Constructor Posicion 1:", complex_table[1])
print("Constructor Posicion 2:", complex_table[2])
print("Constructor Posicion 3:", complex_table[3])
print("Constructor Clave string (x):", complex_table.x)
print("Constructor Clave expresion dinamica (20):", complex_table[20])
print("Constructor Clave indice explicito (30):", complex_table[30])

local function multires_func()
    return "A", "B", "C"
end

local table_multires_last = { "X", multires_func() }
print("Multires al final del constructor:", table_multires_last[1], table_multires_last[2], table_multires_last[3], table_multires_last[4])

local table_multires_mid = { multires_func(), "X" }
print("Multires cortado en el medio:", table_multires_mid[1], table_multires_mid[2], table_multires_mid[3])


