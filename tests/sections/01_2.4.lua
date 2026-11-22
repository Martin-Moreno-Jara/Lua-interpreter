




























































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

