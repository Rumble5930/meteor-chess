package com.rumble5930.chess.core;

import java.util.ArrayList;
import java.util.List;

/**
 * A chess position. Squares: 0 = a1, 7 = h1, 56 = a8, 63 = h8. Pieces: positive = white, negative = black.
 * Positions are treated as immutable: {@link #play(Move)} returns a new position (copy-make).
 */
public final class Pos {
    public static final int P = 1, N = 2, B = 3, R = 4, Q = 5, K = 6;
    public static final int WK = 1, WQ = 2, BK = 4, BQ = 8;
    public static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    private static final int[] N_DF = {1, 2, 2, 1, -1, -2, -2, -1};
    private static final int[] N_DR = {2, 1, -1, -2, -2, -1, 1, 2};
    private static final int[] K_DF = {1, 1, 1, 0, -1, -1, -1, 0};
    private static final int[] K_DR = {1, 0, -1, -1, -1, 0, 1, 1};
    private static final int[] D_DF = {1, 1, -1, -1};
    private static final int[] D_DR = {1, -1, 1, -1};
    private static final int[] O_DF = {1, -1, 0, 0};
    private static final int[] O_DR = {0, 0, 1, -1};

    public final int[] b = new int[64];
    public boolean whiteToMove = true;
    public int castle;
    public int ep = -1;
    public int halfmove;
    public int fullmove = 1;

    // ---------------------------------------------------------------- squares

    public static String sqName(int sq) {
        return "" + (char) ('a' + (sq & 7)) + (char) ('1' + (sq >> 3));
    }

    public static int sqParse(String s) {
        if (s == null || s.length() != 2) return -1;
        int f = s.charAt(0) - 'a';
        int r = s.charAt(1) - '1';
        if (f < 0 || f > 7 || r < 0 || r > 7) return -1;
        return r * 8 + f;
    }

    public static char pieceChar(int p) {
        char c = "?PNBRQK".charAt(Math.abs(p));
        return p > 0 ? c : Character.toLowerCase(c);
    }

    // ---------------------------------------------------------------- FEN

    public static Pos start() {
        return fromFen(START_FEN);
    }

    public static Pos fromFen(String fen) {
        String[] f = fen.trim().split("\\s+");
        if (f.length < 4) throw new IllegalArgumentException("Incomplete FEN");
        Pos p = new Pos();

        String[] rows = f[0].split("/");
        if (rows.length != 8) throw new IllegalArgumentException("FEN needs 8 ranks");
        int whiteKings = 0, blackKings = 0;
        for (int i = 0; i < 8; i++) {
            int r = 7 - i, file = 0;
            for (char c : rows[i].toCharArray()) {
                if (c >= '1' && c <= '8') {
                    file += c - '0';
                    continue;
                }
                int t = switch (Character.toLowerCase(c)) {
                    case 'p' -> P;
                    case 'n' -> N;
                    case 'b' -> B;
                    case 'r' -> R;
                    case 'q' -> Q;
                    case 'k' -> K;
                    default -> throw new IllegalArgumentException("Bad FEN piece: " + c);
                };
                if (file > 7) throw new IllegalArgumentException("FEN rank too long");
                p.b[r * 8 + file] = Character.isUpperCase(c) ? t : -t;
                if (t == K) {
                    if (Character.isUpperCase(c)) whiteKings++;
                    else blackKings++;
                }
                file++;
            }
            if (file != 8) throw new IllegalArgumentException("FEN rank must have 8 files");
        }
        if (whiteKings != 1 || blackKings != 1) throw new IllegalArgumentException("Each side needs exactly one king");

        if (f[1].equals("w")) p.whiteToMove = true;
        else if (f[1].equals("b")) p.whiteToMove = false;
        else throw new IllegalArgumentException("Bad side to move");

        for (char c : f[2].toCharArray()) {
            switch (c) {
                case 'K' -> p.castle |= WK;
                case 'Q' -> p.castle |= WQ;
                case 'k' -> p.castle |= BK;
                case 'q' -> p.castle |= BQ;
                default -> { }
            }
        }
        if (!f[3].equals("-")) {
            p.ep = sqParse(f[3]);
            if (p.ep < 0) throw new IllegalArgumentException("Bad en passant square");
        }
        try {
            if (f.length > 4) p.halfmove = Integer.parseInt(f[4]);
            if (f.length > 5) p.fullmove = Integer.parseInt(f[5]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Bad FEN clock", e);
        }
        return p;
    }

    public String toFen() {
        StringBuilder sb = new StringBuilder();
        for (int r = 7; r >= 0; r--) {
            int empty = 0;
            for (int f = 0; f < 8; f++) {
                int p = b[r * 8 + f];
                if (p == 0) {
                    empty++;
                    continue;
                }
                if (empty > 0) {
                    sb.append(empty);
                    empty = 0;
                }
                sb.append(pieceChar(p));
            }
            if (empty > 0) sb.append(empty);
            if (r > 0) sb.append('/');
        }
        sb.append(whiteToMove ? " w " : " b ");
        if (castle == 0) sb.append('-');
        else {
            if ((castle & WK) != 0) sb.append('K');
            if ((castle & WQ) != 0) sb.append('Q');
            if ((castle & BK) != 0) sb.append('k');
            if ((castle & BQ) != 0) sb.append('q');
        }
        sb.append(' ').append(ep >= 0 ? sqName(ep) : "-");
        sb.append(' ').append(halfmove).append(' ').append(fullmove);
        return sb.toString();
    }

    /** Key for repetition detection (en passant only counts when a capture is actually possible). */
    public String repKey() {
        String[] f = toFen().split(" ");
        return f[0] + " " + f[1] + " " + f[2] + " " + (epCapturable() ? f[3] : "-");
    }

    private boolean epCapturable() {
        if (ep < 0) return false;
        int side = whiteToMove ? 1 : -1;
        int pr = (ep >> 3) - side;
        if (pr < 0 || pr > 7) return false;
        int f = ep & 7;
        for (int df = -1; df <= 1; df += 2) {
            int nf = f + df;
            if (nf >= 0 && nf <= 7 && b[pr * 8 + nf] == side * P) return true;
        }
        return false;
    }

    public Pos copy() {
        Pos n = new Pos();
        System.arraycopy(b, 0, n.b, 0, 64);
        n.whiteToMove = whiteToMove;
        n.castle = castle;
        n.ep = ep;
        n.halfmove = halfmove;
        n.fullmove = fullmove;
        return n;
    }

    // ---------------------------------------------------------------- attacks / check

    public int kingSq(boolean white) {
        int k = white ? K : -K;
        for (int i = 0; i < 64; i++) if (b[i] == k) return i;
        return -1;
    }

    public boolean attacked(int sq, boolean byWhite) {
        int f = sq & 7, r = sq >> 3;
        int s = byWhite ? 1 : -1;

        int pr = byWhite ? r - 1 : r + 1;
        if (pr >= 0 && pr < 8) {
            if (f > 0 && b[pr * 8 + f - 1] == s * P) return true;
            if (f < 7 && b[pr * 8 + f + 1] == s * P) return true;
        }
        for (int i = 0; i < 8; i++) {
            int nf = f + N_DF[i], nr = r + N_DR[i];
            if (nf >= 0 && nf < 8 && nr >= 0 && nr < 8 && b[nr * 8 + nf] == s * N) return true;
            nf = f + K_DF[i];
            nr = r + K_DR[i];
            if (nf >= 0 && nf < 8 && nr >= 0 && nr < 8 && b[nr * 8 + nf] == s * K) return true;
        }
        for (int i = 0; i < 4; i++) {
            int nf = f + D_DF[i], nr = r + D_DR[i];
            while (nf >= 0 && nf < 8 && nr >= 0 && nr < 8) {
                int t = b[nr * 8 + nf];
                if (t != 0) {
                    if (t == s * B || t == s * Q) return true;
                    break;
                }
                nf += D_DF[i];
                nr += D_DR[i];
            }
            nf = f + O_DF[i];
            nr = r + O_DR[i];
            while (nf >= 0 && nf < 8 && nr >= 0 && nr < 8) {
                int t = b[nr * 8 + nf];
                if (t != 0) {
                    if (t == s * R || t == s * Q) return true;
                    break;
                }
                nf += O_DF[i];
                nr += O_DR[i];
            }
        }
        return false;
    }

    public boolean inCheck() {
        return attacked(kingSq(whiteToMove), !whiteToMove);
    }

    // ---------------------------------------------------------------- move generation

    public List<Move> legalMoves() {
        List<Move> ps = new ArrayList<>(64);
        pseudo(ps);
        List<Move> out = new ArrayList<>(ps.size());
        for (Move m : ps) {
            Pos n = play(m);
            if (!n.attacked(n.kingSq(whiteToMove), !whiteToMove)) out.add(m);
        }
        return out;
    }

    public boolean isLegal(Move m) {
        return legalMoves().contains(m);
    }

    /** Finds the legal move matching the UCI string, or null. */
    public Move findLegal(String uci) {
        Move m = Move.parse(uci);
        if (m == null) return null;
        for (Move l : legalMoves()) if (l.equals(m)) return l;
        return null;
    }

    /** True if the move is legal-looking and would be a promotion (used by the GUI to ask for a piece). */
    public boolean isPromotion(int from, int to) {
        int p = b[from];
        if (Math.abs(p) != P) return false;
        int tr = to >> 3;
        return tr == 0 || tr == 7;
    }

    public boolean isCapture(Move m) {
        if (b[m.to()] != 0) return true;
        return Math.abs(b[m.from()]) == P && m.to() == ep && (m.from() & 7) != (m.to() & 7);
    }

    public void pseudo(List<Move> out) {
        int side = whiteToMove ? 1 : -1;
        for (int sq = 0; sq < 64; sq++) {
            int p = b[sq] * side;
            if (p <= 0) continue;
            int f = sq & 7, r = sq >> 3;
            switch (p) {
                case P -> genPawn(out, sq, f, r);
                case N -> {
                    for (int i = 0; i < 8; i++) step(out, sq, f + N_DF[i], r + N_DR[i], side);
                }
                case B -> slide(out, sq, f, r, D_DF, D_DR, side);
                case R -> slide(out, sq, f, r, O_DF, O_DR, side);
                case Q -> {
                    slide(out, sq, f, r, D_DF, D_DR, side);
                    slide(out, sq, f, r, O_DF, O_DR, side);
                }
                case K -> {
                    for (int i = 0; i < 8; i++) step(out, sq, f + K_DF[i], r + K_DR[i], side);
                    genCastle(out, sq);
                }
                default -> { }
            }
        }
    }

    private void step(List<Move> out, int from, int nf, int nr, int side) {
        if (nf < 0 || nf > 7 || nr < 0 || nr > 7) return;
        int to = nr * 8 + nf;
        if (b[to] * side > 0) return;
        out.add(new Move(from, to, 0));
    }

    private void slide(List<Move> out, int from, int f, int r, int[] dfs, int[] drs, int side) {
        for (int i = 0; i < dfs.length; i++) {
            int nf = f + dfs[i], nr = r + drs[i];
            while (nf >= 0 && nf <= 7 && nr >= 0 && nr <= 7) {
                int to = nr * 8 + nf;
                int t = b[to];
                if (t * side > 0) break;
                out.add(new Move(from, to, 0));
                if (t != 0) break;
                nf += dfs[i];
                nr += drs[i];
            }
        }
    }

    private void addPawn(List<Move> out, int from, int to) {
        int tr = to >> 3;
        if (tr == 0 || tr == 7) {
            out.add(new Move(from, to, Q));
            out.add(new Move(from, to, R));
            out.add(new Move(from, to, B));
            out.add(new Move(from, to, N));
        } else {
            out.add(new Move(from, to, 0));
        }
    }

    private void genPawn(List<Move> out, int sq, int f, int r) {
        int dir = whiteToMove ? 1 : -1;
        int startRank = whiteToMove ? 1 : 6;
        int nr = r + dir;
        if (nr < 0 || nr > 7) return;
        int one = nr * 8 + f;
        if (b[one] == 0) {
            addPawn(out, sq, one);
            int two = one + dir * 8;
            if (r == startRank && b[two] == 0) out.add(new Move(sq, two, 0));
        }
        for (int df = -1; df <= 1; df += 2) {
            int nf = f + df;
            if (nf < 0 || nf > 7) continue;
            int to = nr * 8 + nf;
            if (b[to] * dir < 0) addPawn(out, sq, to);
            else if (to == ep && b[to] == 0) out.add(new Move(sq, to, 0));
        }
    }

    private void genCastle(List<Move> out, int sq) {
        if (whiteToMove && sq == 4) {
            if ((castle & WK) != 0 && b[5] == 0 && b[6] == 0 && b[7] == R
                && !attacked(4, false) && !attacked(5, false)) out.add(new Move(4, 6, 0));
            if ((castle & WQ) != 0 && b[3] == 0 && b[2] == 0 && b[1] == 0 && b[0] == R
                && !attacked(4, false) && !attacked(3, false)) out.add(new Move(4, 2, 0));
        } else if (!whiteToMove && sq == 60) {
            if ((castle & BK) != 0 && b[61] == 0 && b[62] == 0 && b[63] == -R
                && !attacked(60, true) && !attacked(61, true)) out.add(new Move(60, 62, 0));
            if ((castle & BQ) != 0 && b[59] == 0 && b[58] == 0 && b[57] == 0 && b[56] == -R
                && !attacked(60, true) && !attacked(59, true)) out.add(new Move(60, 58, 0));
        }
    }

    // ---------------------------------------------------------------- make move

    /** Returns the position after the (assumed pseudo-legal) move. */
    public Pos play(Move m) {
        Pos n = copy();
        int side = whiteToMove ? 1 : -1;
        int p = b[m.from()];
        int t = Math.abs(p);
        boolean capture = b[m.to()] != 0;

        n.b[m.from()] = 0;
        if (t == P && m.to() == ep && b[m.to()] == 0 && (m.from() & 7) != (m.to() & 7)) {
            n.b[m.to() - side * 8] = 0;
            capture = true;
        }
        n.b[m.to()] = m.promo() != 0 ? side * m.promo() : p;

        if (t == K && Math.abs(m.to() - m.from()) == 2) {
            if (m.to() > m.from()) {
                n.b[m.from() + 1] = n.b[m.from() + 3];
                n.b[m.from() + 3] = 0;
            } else {
                n.b[m.from() - 1] = n.b[m.from() - 4];
                n.b[m.from() - 4] = 0;
            }
        }

        int c = castle;
        if (t == K) c &= whiteToMove ? ~(WK | WQ) : ~(BK | BQ);
        c &= rookMask(m.from()) & rookMask(m.to());
        n.castle = c;

        n.ep = (t == P && Math.abs(m.to() - m.from()) == 16) ? (m.from() + m.to()) / 2 : -1;
        n.halfmove = (t == P || capture) ? 0 : halfmove + 1;
        n.fullmove = whiteToMove ? fullmove : fullmove + 1;
        n.whiteToMove = !whiteToMove;
        return n;
    }

    private static int rookMask(int sq) {
        return switch (sq) {
            case 0 -> ~WQ;
            case 7 -> ~WK;
            case 56 -> ~BQ;
            case 63 -> ~BK;
            default -> ~0;
        };
    }
}
