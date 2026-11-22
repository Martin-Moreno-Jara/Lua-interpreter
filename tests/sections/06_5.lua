local void
void = setmetatable({}, {
    __index = function(t, k)
        print("  [Descendiendo al vacio mediante la clave inexistente: " .. tostring(k) .. "]")
        return void 
    end,
    __add = function(a, b)
        return type(b) == "string" and ("Eco final: " .. b) or 0x666
    end,
    __call = function(t, ...)
        local args = table.pack(...)
        return (args.n > 0) and (#args * 0xBAD) or "Silencio"
    end
})

local function summon_tentacles(depth, ...)
    local args = table.pack(...)
    if depth == 0 then
        return setmetatable({ souls = args[1] or "Ninguna" }, {
            __unm = function(t) 
                return - #(tostring(t.souls)) * 100 
            end,
            __concat = function(t, v)
                return t.souls .. " fusionado con " .. tostring(v)
            end
        })
    end
    
    return {
        [depth] = summon_tentacles(depth - 1, ...),
        eye = function(self) return self[depth] end
    }
end

local cthulhu = {
    [ (function() return not not (" 10 " // 3.14) end)() ] = "Realidad Distorsionada (Clave Booleana)",
    
    fractal_core = summon_tentacles(5, "Errantes"),

    chaos = ( ~( -(" -10 " + 0) // 3 ) << 2 ) | 0x0F,

    [ "multires_abyss" ] = { 
        (function() return 1, 2 end)(), 
        (function() return setmetatable({}, {__len = function() return 99 end}), "B", "C" end)(), 
        (function() return "X", "Y" end)() 
    }
}

cthulhu[cthulhu] = "El MDSI por referencia"
cthulhu[void] = cthulhu



























print("\n5.BUCLES DE REFERENCIA:")
print("cthulhu[cthulhu] ->", cthulhu[cthulhu])
print("cthulhu[void] es igual a cthulhu? ->", cthulhu[void] == cthulhu)
