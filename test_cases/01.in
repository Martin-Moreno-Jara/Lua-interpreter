print("--- 2.1 Values and Types ---")
print("Tipo de nil:      ", type(nil))
print("Tipo de boolean:  ", type(true))
print("Tipo de number:   ", type(42))
print("Tipo de string:   ", type("Lua\0String"))
print("Tipo de function: ", type(function() end))
print("Tipo de thread:   ", type(coroutine.create(function() end)))
print("Tipo de userdata: ", type(io.stdin))
print("Tipo de table:    ", type({}))

local val_table = { [1] = "entero" }
val_table[2.0] = "valor insertado con float"
print("Equivalencia float/int en índice (a[2.0] == a[2]):", val_table[2] == "valor insertado con float")

local record = { name = "Analizador" }
print("Syntactic sugar (record.name == record['name']):  ", record.name == record["name"])

print("\n--- 2.2 Scopes, Variables, and Environments ---")
local x = 10
print("Variable global/exterior inicial:", x)
do
    local x = x
    print("  Bloque 1 - local x = x:", x)
    x = x + 1
    do
        local x = x + 1
        print("    Bloque 2 anidado - local x = x + 1:", x)
    end
    print("  Bloque 1 tras anidamiento:", x)
end
print("Variable global/exterior tras los bloques:", x)

local a = {}
local shared_x = 20
for i = 1, 2 do
    local y = 0
    a[i] = function () 
        y = y + 1 
        return shared_x + y 
    end
end
print("Evaluando Closures:")
print("  Closure 1 (Llamada 1):", a[1]())
print("  Closure 1 (Llamada 2):", a[1]())
print("  Closure 2 (Llamada 1):", a[2]())

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

print("\n--- 2.4 Metatables and Metamethods ---")
local CustomType = {
    __add = function(a, b) return a.val + b.val end,
    __sub = function(a, b) return a.val - b.val end,
    __mul = function(a, b) return a.val * b.val end,
    __div = function(a, b) return a.val / b.val end,
    __mod = function(a, b) return a.val % b.val end,
    __pow = function(a, b) return a.val ^ b.val end,
    __unm = function(a) return -a.val end,
    __idiv = function(a, b) return a.val // b.val end,
    __band = function(a, b) return a.val & b.val end,
    __bor  = function(a, b) return a.val | b.val end,
    __bxor = function(a, b) return a.val ~ b.val end,
    __bnot = function(a) return ~a.val end,
    __shl  = function(a, b) return a.val << b.val end,
    __shr  = function(a, b) return a.val >> b.val end,
    __concat = function(a, b) return tostring(a.val) .. tostring(b.val) end,
    __len = function(a) return #(tostring(a.val)) end,
    __eq = function(a, b) return a.val == b.val end,
    __lt = function(a, b) return a.val < b.val end,
    __le = function(a, b) return a.val <= b.val end,
    __index = function(t, k) return "Fallback_Index: " .. tostring(k) end,
    __newindex = function(t, k, v) rawset(t, k, v * 2) end,
    __call = function(func, arg) return "Objeto Invocado: " .. tostring(arg) end
}

local function new_obj(v)
    return setmetatable({ val = v }, CustomType)
end

local obj1 = new_obj(10)
local obj2 = new_obj(5)

print("Aritmética (10 + 5):", obj1 + obj2)
print("Floor Division (10 // 5):", obj1 // obj2)
print("Bitwise AND (10 & 5):", obj1 & obj2)
print("Bitwise Shift Left (10 << 5):", obj1 << obj2)
print("Concatenación (10 .. 5):", obj1 .. obj2)
print("Igualdad (10 == 10):", obj1 == new_obj(10))
print("Menor que (5 < 10):", obj2 < obj1)

local obj3 = new_obj(0)
obj3.dinamico = 50
print("Asignación con __newindex y rawset (50 * 2):", obj3.dinamico)
print("Búsqueda de clave inexistente (__index):", obj3.inexistente)
print("Llamada directa al objeto (__call):", obj1("test"))

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

print("\n--- 2.6 Coroutines ---")
local function foo (a)
    print("foo", a)
    return coroutine.yield(2*a)
end

local co = coroutine.create(function (a,b)
    print("co-body", a, b)
    local r = foo(a+1)
    print("co-body", r)
    local r2, s2 = coroutine.yield(a+b, a-b)
    print("co-body", r2, s2)
    return b, "end"
end)

print("main", coroutine.resume(co, 1, 10))
print("main", coroutine.resume(co, "r"))
print("main", coroutine.resume(co, "x", "y"))
print("main", coroutine.resume(co, "x", "y"))