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

