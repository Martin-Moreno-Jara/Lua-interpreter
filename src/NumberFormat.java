import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/** Convierte números decimales a texto igual que Lua: hasta 14 cifras significativas. */
// String.format de Java no da el mismo resultado que Lua en todos los casos, por eso se arma a mano.
public final class NumberFormat {
    private NumberFormat() {}

    // Cifras significativas: las que quedan al quitar los ceros de la izquierda.
    private static final MathContext SIG14 = new MathContext(14, RoundingMode.HALF_EVEN);

    /** Texto de un número decimal tal como lo imprime Lua, por ejemplo 3.0, 0.1 o 1e+15. */
    public static String format(double d) {
        if (Double.isNaN(d)) return "nan";
        if (Double.isInfinite(d)) return d > 0 ? "inf" : "-inf";
        String s = g14(d);
        if (s.matches("-?[0-9]+")) s += ".0"; // Lua imprime 3.0, no 3
        return s;
    }

    /** Redondea a 14 cifras y elige entre escritura normal y escritura con exponente ("1e+15"). */
    private static String g14(double d) {
        if (d == 0) return (1 / d < 0) ? "-0" : "0"; // distingue 0 de -0, porque Lua imprime -0.0

        // new BigDecimal(d) parte del valor exacto guardado y redondea igual que Lua; BigDecimal.valueOf(d) no
        BigDecimal bd = new BigDecimal(d).round(SIG14);
        int exp = bd.precision() - bd.scale() - 1; // posición de la primera cifra: 0 para 1.5, 2 para 150

        // Si el número no es ni muy grande ni muy pequeño, Lua lo escribe sin exponente
        if (exp >= -4 && exp < 14) return bd.stripTrailingZeros().toPlainString();

        // Con exponente: una cifra, punto, el resto de cifras sin ceros finales y "e+NN"
        String digits = bd.unscaledValue().abs().toString();
        int end = digits.length();
        while (end > 1 && digits.charAt(end - 1) == '0') end--;
        StringBuilder sb = new StringBuilder();
        if (bd.signum() < 0) sb.append('-');
        sb.append(digits.charAt(0));
        if (end > 1) sb.append('.').append(digits, 1, end);
        sb.append('e').append(exp < 0 ? '-' : '+');
        int a = Math.abs(exp);
        if (a < 10) sb.append('0'); // Lua escribe el exponente con al menos dos cifras: 1e+05
        sb.append(a);
        return sb.toString();
    }
}
