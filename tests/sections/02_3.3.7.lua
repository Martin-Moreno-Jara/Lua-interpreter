

















































































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

