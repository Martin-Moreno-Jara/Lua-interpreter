












print("\n--- 3.4.2 Bitwise Operators ---")
local b1 = 0xA
local b2 = 0x3
print("Bitwise AND (&):", b1 & b2)
print("Bitwise OR (|):", b1 | b2)
print("Bitwise XOR (~):", b1 ~ b2)
print("Bitwise NOT (~ unario):", ~b1)
print("Shift Right (>>):", b1 >> 1)
print("Shift Left (<<):", b1 << 2)
print("Shift Negativo (equivale a direccion opuesta):", b1 << -1)
print("Shift Mayor a bits (debe ser 0):", b1 >> 100)

