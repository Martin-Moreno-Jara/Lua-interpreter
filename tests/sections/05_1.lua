local function frankenstein(...)
    return setmetatable({
        (function(a, b) return (a or 0) << (b and 2 or 0) end)(10, true),
        
        [ (2^3 + "10.5" // 2) & 0xFF ] = function(x, y) 
            return (x == type(y)) and (x .. " es valido") or (#tostring(y) * (x | 0))
        end,

        nested = setmetatable({
            base = { table.unpack(table.pack(...), 1, table.pack(...).n) },
            
            method = function(self, val)
                local a, b = (function() return val ~ 0x0F, val >> 1 end)()
                return (a > b and a or b) + #(self.base)
            end,
            
            [ {1, 2, 3} ] = "Clave tabla-referencia (perdida en el limbo)"
        }, {
            __call = function(t, a) return t:method(a) end,
            __add = function(t, v) return t.base[1] .. v end
        }),

        multires_array = { (function() return 1, 2, (function() return 3, 4 end)() end)() },

        crazy_expr = (not (10 == "10") and (#"Lua" == 3)) and (("5" + 5) * 2 ^ 2) or ("Fallback" .. 99)

    }, {
        __index = function(t, k)
            return type(k) == "number" and ("Indice " .. k .. " ausente. Calculo bitwise fallback: " .. (k ~ 0xAA)) or nil
        end
    })
end

local monster = frankenstein("Aberracion", "Semantica", "Completada", 42)

print("1. Elemento posicional via IIFE y bitwise left shift:", monster[1])

