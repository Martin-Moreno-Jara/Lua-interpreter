


































print("\n--- 3.3.4 Control Structures ---")
local condition = false
if not condition then
    print("Estructura if: condicion false detectada como true via 'not'")
elseif condition then
    print("Esto no deberia ejecutarse")
end

local count = 3
while count > 0 do
    print("while loop:", count)
    count = count - 1
end

repeat
    print("repeat/until loop:", count)
    count = count + 1
until count >= 3

print("Prueba de goto y labels:")
local loop_counter = 0
::start_label::
loop_counter = loop_counter + 1
if loop_counter < 3 then
    print("  salto goto", loop_counter)
    goto start_label
end
print("  ejecucion continua despues del goto")

