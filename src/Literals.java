// Contrato §5.7 (dueño: B). Decodificación de literales; la usa el evaluador (A).
public final class Literals {
    private Literals() {}

    // Texto de un token INT, HEX, FLOAT o HEX_FLOAT -> LuaInteger o LuaFloat.
    // Ej.: "345", "0xBEBADA", "314.16e-2", "0X1.921FB54442D18P+1".
    public static LuaValue parseNumber(String tokenText) {
        throw new UnsupportedOperationException("TODO B: Literals.parseNumber");
    }

    // Texto completo de un token NORMALSTRING, CHARSTRING o LONGSTRING,
    // incluidas comillas o corchetes -> contenido decodificado (un char por byte).
    // Escapes \n \t \0 \xXX \ddd, barra-u{XXX} (a UTF-8) y \z; los strings largos no
    // (Ojo: Java procesa la barra seguida de u como escape Unicode incluso en comentarios.)
    // procesan escapes y omiten el primer salto de línea.
    public static String parseString(String tokenText) {
        throw new UnsupportedOperationException("TODO B: Literals.parseString");
    }
}
