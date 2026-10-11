package com.rumble5930.chess.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A chess game: move history, result detection (mate, stalemate, 50 moves, repetition, material), SAN and PGN. */
public final class Game {
    public enum Result { ONGOING, WHITE_WINS, BLACK_WINS, DRAW }

    private final List<Pos> positions = new ArrayList<>();
    private final List<String> keys = new ArrayList<>();
    private final List<Move> moves = new ArrayList<>();
    private final List<String> sans = new ArrayList<>();
    private Result result = Result.ONGOING;
    private String reason = "";

    public Game() {
        Pos start = Pos.start();
        positions.add(start);
        keys.add(start.repKey());
    }

    public Pos pos() {
        return positions.get(positions.size() - 1);
    }

    public int plyCount() {
        return moves.size();
    }

    public List<Move> moves() {
        return Collections.unmodifiableList(moves);
    }

    public List<String> sans() {
        return Collections.unmodifiableList(sans);
    }

    public Move lastMove() {
        return moves.isEmpty() ? null : moves.get(moves.size() - 1);
    }

    public Result result() {
        return result;
    }

    public String reason() {
        return reason;
    }

    public boolean isOver() {
        return result != Result.ONGOING;
    }

    /** Plays a move if the game is running and the move is legal. */
    public boolean play(Move m) {
        if (isOver()) return false;
        Pos p = pos();
        List<Move> legal = p.legalMoves();
        if (!legal.contains(m)) return false;

        sans.add(san(p, m, legal));
        moves.add(m);
        Pos n = p.play(m);
        positions.add(n);
        keys.add(n.repKey());
        updateResult();
        return true;
    }

    public boolean playUci(String uci) {
        Move m = pos().findLegal(uci);
        return m != null && play(m);
    }

    private void updateResult() {
        Pos p = pos();
        if (p.legalMoves().isEmpty()) {
            if (p.inCheck()) {
                result = p.whiteToMove ? Result.BLACK_WINS : Result.WHITE_WINS;
                reason = "Checkmate";
            } else {
                result = Result.DRAW;
                reason = "Stalemate";
            }
            return;
        }
        if (insufficientMaterial(p)) {
            result = Result.DRAW;
            reason = "Insufficient material";
            return;
        }
        if (p.halfmove >= 100) {
            result = Result.DRAW;
            reason = "Fifty-move rule";
            return;
        }
        String key = keys.get(keys.size() - 1);
        int count = 0;
        for (String k : keys) if (k.equals(key)) count++;
        if (count >= 3) {
            result = Result.DRAW;
            reason = "Threefold repetition";
        }
    }

    private static boolean insufficientMaterial(Pos p) {
        int minors = 0;
        for (int v : p.b) {
            int t = Math.abs(v);
            if (t == Pos.P || t == Pos.R || t == Pos.Q) return false;
            if (t == Pos.N || t == Pos.B) minors++;
        }
        return minors <= 1;
    }

    public void resign(boolean whiteResigns) {
        if (isOver()) return;
        result = whiteResigns ? Result.BLACK_WINS : Result.WHITE_WINS;
        reason = (whiteResigns ? "White" : "Black") + " resigned";
    }

    public void drawByAgreement() {
        if (isOver()) return;
        result = Result.DRAW;
        reason = "Draw by agreement";
    }

    public void abandon(boolean whiteLeft) {
        if (isOver()) return;
        result = whiteLeft ? Result.BLACK_WINS : Result.WHITE_WINS;
        reason = (whiteLeft ? "White" : "Black") + " left the game";
    }

    // ---------------------------------------------------------------- notation

    public static String san(Pos p, Move m, List<Move> legal) {
        int piece = Math.abs(p.b[m.from()]);
        boolean capture = p.isCapture(m);
        StringBuilder sb = new StringBuilder();

        if (piece == Pos.K && Math.abs(m.to() - m.from()) == 2) {
            sb.append(m.to() > m.from() ? "O-O" : "O-O-O");
        } else {
            if (piece == Pos.P) {
                if (capture) sb.append((char) ('a' + (m.from() & 7))).append('x');
            } else {
                sb.append("PNBRQK".charAt(piece - 1));
                boolean sameTo = false, sameFile = false, sameRank = false;
                for (Move o : legal) {
                    if (o.from() == m.from() || o.to() != m.to() || Math.abs(p.b[o.from()]) != piece) continue;
                    sameTo = true;
                    if ((o.from() & 7) == (m.from() & 7)) sameFile = true;
                    if ((o.from() >> 3) == (m.from() >> 3)) sameRank = true;
                }
                if (sameTo) {
                    String from = Pos.sqName(m.from());
                    if (!sameFile) sb.append(from.charAt(0));
                    else if (!sameRank) sb.append(from.charAt(1));
                    else sb.append(from);
                }
                if (capture) sb.append('x');
            }
            sb.append(Pos.sqName(m.to()));
            if (m.promo() != 0) sb.append('=').append("PNBRQK".charAt(m.promo() - 1));
        }

        Pos n = p.play(m);
        if (n.inCheck()) sb.append(n.legalMoves().isEmpty() ? '#' : '+');
        return sb.toString();
    }

    public String resultTag() {
        return switch (result) {
            case WHITE_WINS -> "1-0";
            case BLACK_WINS -> "0-1";
            case DRAW -> "1/2-1/2";
            case ONGOING -> "*";
        };
    }

    public String pgn(String white, String black) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Event \"Meteor Chess\"]\n");
        sb.append("[White \"").append(white).append("\"]\n");
        sb.append("[Black \"").append(black).append("\"]\n");
        sb.append("[Result \"").append(resultTag()).append("\"]\n\n");
        for (int i = 0; i < sans.size(); i++) {
            if (i % 2 == 0) sb.append(i / 2 + 1).append(". ");
            sb.append(sans.get(i)).append(' ');
        }
        sb.append(resultTag());
        return sb.toString();
    }
}
