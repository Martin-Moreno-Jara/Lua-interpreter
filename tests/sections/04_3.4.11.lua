













































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


