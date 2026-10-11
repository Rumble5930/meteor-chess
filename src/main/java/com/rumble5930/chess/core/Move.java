package com.rumble5930.chess.core;

/** A move: from/to squares (0 = a1 ... 63 = h8) and optional promotion piece type (N, B, R or Q; 0 if none). */
public record Move(int from, int to, int promo) {
    public Move(int from, int to) {
        this(from, to, 0);
    }

    /** Long algebraic / UCI notation, e.g. "e2e4" or "e7e8q". */
    public String uci() {
        String s = Pos.sqName(from) + Pos.sqName(to);
        if (promo != 0) s += "?pnbrqk".charAt(promo);
        return s;
    }

    /** Parses UCI notation. Returns null when malformed. */
    public static Move parse(String s) {
        if (s == null || (s.length() != 4 && s.length() != 5)) return null;
        int from = Pos.sqParse(s.substring(0, 2));
        int to = Pos.sqParse(s.substring(2, 4));
        if (from < 0 || to < 0) return null;
        int promo = 0;
        if (s.length() == 5) {
            promo = switch (Character.toLowerCase(s.charAt(4))) {
                case 'n' -> Pos.N;
                case 'b' -> Pos.B;
                case 'r' -> Pos.R;
                case 'q' -> Pos.Q;
                default -> -1;
            };
            if (promo < 0) return null;
        }
        return new Move(from, to, promo);
    }
}
