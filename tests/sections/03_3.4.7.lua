








































































print("\n--- 3.4.7 The Length Operator ---")
print("Longitud de String (#):", #"Hola Mundo")

local seq = {10, 20, 30, 40, 50}
print("Longitud de Secuencia Pura:", #seq)

local non_seq_hole = {10, 20, 30, nil, 50}
print("Longitud con Agujero (Border puede ser 3 o 5):", #non_seq_hole)

local non_seq_start = {nil, 20, 30}
print("Longitud con Agujero Inicial (Border puede ser 0 o 3):", #non_seq_start)

local seq_empty = {}
print("Longitud de Tabla Vacia:", #seq_empty)

