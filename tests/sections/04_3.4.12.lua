












































































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
