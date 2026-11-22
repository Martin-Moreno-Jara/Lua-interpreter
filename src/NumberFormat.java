// Interno de B (lo usa Ops.tostring). Formato de números como lua5.4.
public final class NumberFormat {
    private NumberFormat() {}

    // %.14g de C, con ".0" si parece entero; "inf", "-inf", "nan".
    // Ej.: 3.1415926535898, 21.0, 8.0. String.format("%.14g") de Java NO es equivalente.
    public static String format(double d) {
        throw new UnsupportedOperationException("TODO B: NumberFormat.format");
    }
}
