
















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

