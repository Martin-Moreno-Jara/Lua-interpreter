import java.math.BigDecimal;
import java.math.MathContext;

// STUB LOCAL de A para probar el evaluador. NO va a main: la versión real es de B.
// Aproximación de %.14g: 14 cifras significativas, sin notación exponencial.
public final class NumberFormat {
    private NumberFormat() {}

    public static String format(double d) {
        if (Double.isNaN(d)) return (1 / d < 0) ? "-nan" : "nan";
        if (Double.isInfinite(d)) return d > 0 ? "inf" : "-inf";
        if (d == Math.rint(d) && Math.abs(d) < 1e15) return String.valueOf((long) d) + ".0";
        String s = new BigDecimal(d).round(new MathContext(14)).stripTrailingZeros().toPlainString();
        return s;
    }
}
