package com.rumble5930.chess.engine;

import com.rumble5930.chess.core.Move;
import com.rumble5930.chess.core.Pos;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Small alpha-beta engine: iterative deepening, quiescence search, check extensions, MVV-LVA ordering,
 * piece-square evaluation. Not Stockfish, but plays sensible chess and has no external dependencies.
 * One instance = one search at a time; {@link #cancel()} may be called from another thread.
 */
public final class Engine {
    public record Result(Move move, int score, int depth, long nodes) {}

    private static final int INF = 1_000_000;
    private static final int MATE = 100_000;
    private static final int[] VAL = {0, 100, 320, 330, 500, 900, 0};

    private static final int[] PST_P = {
        0, 0, 0, 0, 0, 0, 0, 0,
        50, 50, 50, 50, 50, 50, 50, 50,
        10, 10, 20, 30, 30, 20, 10, 10,
        5, 5, 10, 25, 25, 10, 5, 5,
        0, 0, 0, 20, 20, 0, 0, 0,
        5, -5, -10, 0, 0, -10, -5, 5,
        5, 10, 10, -20, -20, 10, 10, 5,
        0, 0, 0, 0, 0, 0, 0, 0};
    private static final int[] PST_N = {
        -50, -40, -30, -30, -30, -30, -40, -50,
        -40, -20, 0, 0, 0, 0, -20, -40,
        -30, 0, 10, 15, 15, 10, 0, -30,
        -30, 5, 15, 20, 20, 15, 5, -30,
        -30, 0, 15, 20, 20, 15, 0, -30,
        -30, 5, 10, 15, 15, 10, 5, -30,
        -40, -20, 0, 5, 5, 0, -20, -40,
        -50, -40, -30, -30, -30, -30, -40, -50};
    private static final int[] PST_B = {
        -20, -10, -10, -10, -10, -10, -10, -20,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -10, 0, 5, 10, 10, 5, 0, -10,
        -10, 5, 5, 10, 10, 5, 5, -10,
        -10, 0, 10, 10, 10, 10, 0, -10,
        -10, 10, 10, 10, 10, 10, 10, -10,
        -10, 5, 0, 0, 0, 0, 5, -10,
        -20, -10, -10, -10, -10, -10, -10, -20};
    private static final int[] PST_R = {
        0, 0, 0, 0, 0, 0, 0, 0,
        5, 10, 10, 10, 10, 10, 10, 5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        -5, 0, 0, 0, 0, 0, 0, -5,
        0, 0, 0, 5, 5, 0, 0, 0};
    private static final int[] PST_Q = {
        -20, -10, -10, -5, -5, -10, -10, -20,
        -10, 0, 0, 0, 0, 0, 0, -10,
        -10, 0, 5, 5, 5, 5, 0, -10,
        -5, 0, 5, 5, 5, 5, 0, -5,
        0, 0, 5, 5, 5, 5, 0, -5,
        -10, 5, 5, 5, 5, 5, 0, -10,
        -10, 0, 5, 0, 0, 0, 0, -10,
        -20, -10, -10, -5, -5, -10, -10, -20};
    private static final int[] PST_K_MID = {
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -30, -40, -40, -50, -50, -40, -40, -30,
        -20, -30, -30, -40, -40, -30, -30, -20,
        -10, -20, -20, -20, -20, -20, -20, -10,
        20, 20, 0, 0, 0, 0, 20, 20,
        20, 30, 10, 0, 0, 10, 30, 20};
    private static final int[] PST_K_END = {
        -50, -40, -30, -20, -20, -30, -40, -50,
        -30, -20, -10, 0, 0, -10, -20, -30,
        -30, -10, 20, 30, 30, 20, -10, -30,
        -30, -10, 30, 40, 40, 30, -10, -30,
        -30, -10, 30, 40, 40, 30, -10, -30,
        -30, -10, 20, 30, 30, 20, -10, -30,
        -30, -30, 0, 0, 0, 0, -30, -30,
        -50, -30, -30, -30, -30, -30, -30, -50};

    private final Random rng = new Random();
    private volatile boolean cancelled;
    private long deadline;
    private boolean aborted;
    private long nodes;

    public void cancel() {
        cancelled = true;
    }

    /** Searches the position. Returns null if there is no legal move. */
    public Result search(Pos pos, int maxDepth, long millis, int noise) {
        cancelled = false;
        aborted = false;
        nodes = 0;
        deadline = System.currentTimeMillis() + millis;

        List<Move> root = pos.legalMoves();
        if (root.isEmpty()) return null;
        if (root.size() == 1) return new Result(root.get(0), 0, 0, 0);

        order(pos, root);
        Move best = root.get(0);
        int bestScore = 0;
        int reached = 0;

        for (int d = 1; d <= maxDepth; d++) {
            List<Move> ordered = new ArrayList<>(root);
            ordered.remove(best);
            ordered.add(0, best);

            Move iterBest = null;
            int iterScore = -INF;
            int alpha = -INF;
            for (Move m : ordered) {
                Pos child = pos.play(m);
                int score;
                if (noise > 0) {
                    score = -negamax(child, d - 1, -INF, INF, 1);
                    if (!aborted) score += rng.nextInt(noise * 2 + 1) - noise;
                } else {
                    score = -negamax(child, d - 1, -INF, -alpha, 1);
                }
                if (aborted) break;
                if (score > iterScore) {
                    iterScore = score;
                    iterBest = m;
                }
                if (score > alpha) alpha = score;
            }

            if (iterBest != null) {
                best = iterBest;
                bestScore = iterScore;
                if (!aborted) reached = d;
            }
            if (aborted) break;
            if (Math.abs(bestScore) > MATE - 200 && noise == 0) break;
        }
        return new Result(best, bestScore, reached, nodes);
    }

    private boolean checkAbort() {
        if ((nodes & 1023) == 0 && (cancelled || System.currentTimeMillis() > deadline)) aborted = true;
        return aborted;
    }

    private int negamax(Pos p, int depth, int alpha, int beta, int ply) {
        nodes++;
        if (checkAbort()) return 0;

        boolean inCheck = p.inCheck();
        if (inCheck && ply < 24) depth++;
        if (depth <= 0) return quiesce(p, alpha, beta, ply);
        if (p.halfmove >= 100) return 0;

        List<Move> moves = p.legalMoves();
        if (moves.isEmpty()) return inCheck ? -MATE + ply : 0;
        order(p, moves);

        int best = -INF;
        for (Move m : moves) {
            int score = -negamax(p.play(m), depth - 1, -beta, -alpha, ply + 1);
            if (aborted) return 0;
            if (score > best) best = score;
            if (best > alpha) alpha = best;
            if (alpha >= beta) break;
        }
        return best;
    }

    private int quiesce(Pos p, int alpha, int beta, int ply) {
        nodes++;
        if (checkAbort()) return 0;

        boolean inCheck = p.inCheck();
        int best;
        List<Move> moves;
        if (inCheck) {
            moves = p.legalMoves();
            if (moves.isEmpty()) return -MATE + ply;
            best = -INF;
        } else {
            best = eval(p);
            if (best >= beta) return best;
            if (best > alpha) alpha = best;
            if (ply > 48) return best;
            moves = tacticalMoves(p);
        }
        order(p, moves);

        for (Move m : moves) {
            int score = -quiesce(p.play(m), -beta, -alpha, ply + 1);
            if (aborted) return 0;
            if (score > best) best = score;
            if (best > alpha) alpha = best;
            if (alpha >= beta) break;
        }
        return best;
    }

    private static List<Move> tacticalMoves(Pos p) {
        List<Move> ps = new ArrayList<>(48);
        p.pseudo(ps);
        List<Move> out = new ArrayList<>(16);
        for (Move m : ps) {
            if (!p.isCapture(m) && m.promo() == 0) continue;
            Pos n = p.play(m);
            if (!n.attacked(n.kingSq(p.whiteToMove), !p.whiteToMove)) out.add(m);
        }
        return out;
    }

    private static void order(Pos p, List<Move> moves) {
        int n = moves.size();
        int[] scores = new int[n];
        for (int i = 0; i < n; i++) scores[i] = moveScore(p, moves.get(i));
        // insertion sort, descending (lists are short)
        for (int i = 1; i < n; i++) {
            Move m = moves.get(i);
            int s = scores[i];
            int j = i - 1;
            while (j >= 0 && scores[j] < s) {
                moves.set(j + 1, moves.get(j));
                scores[j + 1] = scores[j];
                j--;
            }
            moves.set(j + 1, m);
            scores[j + 1] = s;
        }
    }

    private static int moveScore(Pos p, Move m) {
        int attacker = Math.abs(p.b[m.from()]);
        int victim = Math.abs(p.b[m.to()]);
        int s = 0;
        if (victim != 0) s = 10_000 + 10 * VAL[victim] - VAL[attacker];
        else if (p.isCapture(m)) s = 10_000 + 10 * VAL[Pos.P] - VAL[Pos.P];
        if (m.promo() != 0) s += 5_000 + VAL[m.promo()];
        return s;
    }

    /** Static evaluation in centipawns from the point of view of the side to move. */
    public static int eval(Pos p) {
        int nonPawn = 0;
        for (int v : p.b) {
            int t = Math.abs(v);
            if (t >= Pos.N && t <= Pos.Q) nonPawn += VAL[t];
        }
        boolean endgame = nonPawn <= 2600;

        int score = 0;
        for (int sq = 0; sq < 64; sq++) {
            int v = p.b[sq];
            if (v == 0) continue;
            int t = Math.abs(v);
            int idx = v > 0 ? (7 - (sq >> 3)) * 8 + (sq & 7) : (sq >> 3) * 8 + (sq & 7);
            int val = VAL[t] + pst(t, idx, endgame);
            score += v > 0 ? val : -val;
        }
        return p.whiteToMove ? score : -score;
    }

    private static int pst(int t, int idx, boolean endgame) {
        return switch (t) {
            case Pos.P -> PST_P[idx];
            case Pos.N -> PST_N[idx];
            case Pos.B -> PST_B[idx];
            case Pos.R -> PST_R[idx];
            case Pos.Q -> PST_Q[idx];
            default -> endgame ? PST_K_END[idx] : PST_K_MID[idx];
        };
    }
}
