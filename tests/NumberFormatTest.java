import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Pruebas unitarias de NumberFormat: cada número debe imprimirse igual que
 * print(x) en lua5.4. Los valores esperados se sacaron de lua5.4 real.
 */
public class NumberFormatTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
        // si parece entero, Lua le agrega .0
        "3.0, 3.0",
        "21.0, 21.0",
        "8.0, 8.0",
        "-0.0, -0.0",
        // decimales normales
        "0.5, 0.5",
        "0.1, 0.1",
        "3.5, 3.5",
        "0.0001, 0.0001",
        // Lua solo muestra 14 cifras
        "0.3333333333333333, 0.33333333333333",
        "33.333333333333336, 33.333333333333",
        "3.14159265358979, 3.1415926535898",
        // números muy grandes o muy pequeños se escriben con e
        "1e14, 1e+14",
        "1e15, 1e+15",
        "123456789012345.0, 1.2345678901234e+14",
        "99999999999999.5, 1e+14",
        "9007199254740992.0, 9.007199254741e+15",
        "9.223372036854775807e18, 9.2233720368548e+18",
        "1e100, 1e+100",
        "0.00001, 1e-05",
        "-0.00001, -1e-05",
        "2.5e-300, 2.5e-300",
        // infinitos
        "Infinity, inf",
        "-Infinity, -inf",
    })
    void formatoIgualQueLua(double input, String expected) {
        assertEquals(expected, NumberFormat.format(input));
    }
}
