print("--- 2.1 Values and Types ---")
print("Tipo de nil:      ", type(nil))
print("Tipo de boolean:  ", type(true))
print("Tipo de number:   ", type(42))
print("Tipo de string:   ", type("Lua\0String"))
print("Tipo de function: ", type(function() end))
print("Tipo de thread:   ", type(coroutine.create(function() end)))
print("Tipo de userdata: ", type(io.stdin))
print("Tipo de table:    ", type({}))

local val_table = { [1] = "entero" }
val_table[2.0] = "valor insertado con float"
print("Equivalencia float/int en índice (a[2.0] == a[2]):", val_table[2] == "valor insertado con float")

local record = { name = "Analizador" }
print("Syntactic sugar (record.name == record['name']):  ", record.name == record["name"])

