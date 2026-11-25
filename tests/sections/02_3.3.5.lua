































































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

