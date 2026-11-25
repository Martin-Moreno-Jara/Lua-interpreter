import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

public final class NumberFormat {
    private NumberFormat() {
    }

    private static final MathContext SIG14 = new MathContext(14, RoundingMode.HALF_EVEN);

    public static String format(double d) {
        if (Double.isNaN(d))
            return "nan";
        if (Double.isInfinite(d))
            return d > 0 ? "inf" : "-inf";
        String s = g14(d);
        if (s.matches("-?[0-9]+"))
            s += ".0"; // Lua imprime 3.0, no 3
        return s;
    }

    private static String g14(double d) {
        if (d == 0)
            return (1 / d < 0) ? "-0" : "0"; // distingue 0 de -0, porque Lua imprime -0.0

        // new BigDecimal(d) redondea igual que Lua; BigDecimal.valueOf(d) no
        BigDecimal bd = new BigDecimal(d).round(SIG14);
        int exp = bd.precision() - bd.scale() - 1; // exponente decimal: d = m * 10^exp

        // si el número no es ni muy grande ni muy pequeño, Lua lo escribe sin "e"
        if (exp >= -4 && exp < 14) {
            return bd.stripTrailingZeros().toPlainString();
        }

        String digits = bd.unscaledValue().abs().toString();
        int end = digits.length();
        while (end > 1 && digits.charAt(end - 1) == '0')
            end--;
        StringBuilder sb = new StringBuilder();
        if (bd.signum() < 0)
            sb.append('-');
        sb.append(digits.charAt(0));
        if (end > 1)
            sb.append('.').append(digits, 1, end);
        sb.append('e').append(exp < 0 ? '-' : '+');
        int a = Math.abs(exp);
        if (a < 10)
            sb.append('0');
        sb.append(a);
        return sb.toString();
    }
}
