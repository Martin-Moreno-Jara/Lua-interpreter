print("--- 3.1 Lexical Conventions ---")
local str_escapes = "Linea1\n\tTab\x41\066\u{2603}\z
    ContinuacionSinEspacios"
local long_str = [==[
Texto
Multilinea
Ignora secuencias \n y usa literal]==]
local num_int = 345
local num_hex = 0xBEBADA
local num_float = 314.16e-2
local num_hex_float = 0X1.921FB54442D18P+1

print(str_escapes)
print(long_str)
print(num_int, num_hex, num_float, num_hex_float)

print("\n--- 3.2 y 3.3.3 Variables y Asignacion Multiple ---")
local a, b, c = 1, 2, 3
print("Original:", a, b, c)
a, b, c = c, a, b
print("Permutado (a,b,c = c,a,b):", a, b, c)

local t = {}
local i = 1
i, t[i] = i + 1, 20
print("Evaluacion estricta de indices:")
print("i:", i, "t[1]:", t[1], "t[2]:", t[2])

print("\n--- 3.3.1 y 3.3.6 Bloques y Llamadas ---")
do
    local block_var = "Variable de bloque explicito"
    print(block_var)
end
;(print or io.write)("Llamada desambiguada con punto y coma inicial\n")

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

print("\n--- 3.3.5 For Statement ---")
for n = 1, 5, 2 do
    print("For numerico (step 2):", n)
end

for n = 10.5, 9.0, -0.5 do
    print("For numerico flotante (step -0.5):", n)
end

local function iterador(estado, control)
    if control >= 3 then return nil end
    return control + 1, (control + 1) * 10
end

for k, v in iterador, nil, 0 do
    print("For generico:", k, v)
end

print("\n--- 3.3.7 y 3.3.8 Variable Declarations (<const> y <close>) ---")
local x <const> = 100
print("Variable const local:", x)

local function test_to_be_closed()
    local cerrado_normal = false
    local RecursoNormal = setmetatable({}, {
        __close = function(self)
            cerrado_normal = true
            print("  -> Recurso cerrado por finalizacion de bloque")
        end
    })

    do
        local r_normal <close> = RecursoNormal
        print("  Dentro del bloque con variable <close> normal")
    end
    print("  Estado de cierre normal:", cerrado_normal)
    
    do
        local r_error <close> = setmetatable({}, {
            __close = function(self, err) 
                print("  -> Recurso cerrado por interrupcion de error. Motivo:", err) 
            end
        })
        print("  Provocando error intencional para evaluar __close...")
        error("FalloCriticoSimulado")
    end
end

local pcall_status, pcall_err = pcall(test_to_be_closed)
print("Estado post-pcall:", pcall_status)

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