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


print("\n--- 3.4.11 Function Definitions y Variadics ---")
global_func_sugar = function() return "asignacion de funcion" end
print("Global sugar translation:", global_func_sugar())

local function local_recursive(n)
    if n == 0 then return 0 end
    return n + local_recursive(n - 1)
end
print("Local recursiva instanciada correctamente:", local_recursive(3))
local function test_variadic(a, b, ...)
    local v_args = table.pack(...) 
    
    print("Variadic Arg a:", a)
    print("Variadic Arg b:", b)
    print("Numero de extra args (v_args.n):", v_args.n)
    if v_args.n > 0 then
        print("Primer extra arg:", v_args[1])
    end
end

test_variadic(3)
test_variadic(3, 4)
test_variadic(3, 4, 5, 8)

local function test_variadic_legacy(a, b, ...)
    local args_pack = {...}
    print("Legacy Variadic (sin nombre) length:", #args_pack)
end
test_variadic_legacy(10, 20, 30, 40)


print("\n--- 3.4.12 Lists of Expressions, Multiple Results, and Adjustment ---")
local function f_multi() return 10, 20, 30 end
local function g_multi() return "a", "b" end

local var1, var2, var3 = f_multi()
print("Asignacion multiple exacta:", var1, var2, var3)

local v_ex1, v_ex2 = f_multi()
print("Asignacion con extras truncados:", v_ex1, v_ex2)

local v_m1, v_m2, v_m3, v_m4 = f_multi()
print("Asignacion con faltantes llenados con nil:", v_m1, v_m2, v_m3, v_m4)

local comb1, comb2, comb3 = g_multi(), f_multi()
print("Asignacion truncada + multiple final:", comb1, comb2, comb3)

local parens_trunc = (f_multi())
print("Forzado a 1 resultado mediante parentesis:", parens_trunc)

local function print_wrapper(...)
    print("Print wrapper recibiendo multires:", ...)
end
print_wrapper("f_multi retorna:", f_multi())
print_wrapper("g_multi envuelto en parentesis retorna:", (g_multi()))

local function return_multires_tail()
    return f_multi()
end
local tail1, tail2, tail3 = return_multires_tail()
print("Retorno multires desde otra funcion (Tail call propagation):", tail1, tail2, tail3)