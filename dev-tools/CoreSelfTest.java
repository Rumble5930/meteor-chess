import com.rumble5930.chess.core.*;
import com.rumble5930.chess.engine.*;
import java.util.List;

public class CoreSelfTest {
    static int fails = 0;

    static long perft(Pos p, int d) {
        if (d == 0) return 1;
        List<Move> ms = p.legalMoves();
        if (d == 1) return ms.size();
        long n = 0;
        for (Move m : ms) n += perft(p.play(m), d - 1);
        return n;
    }

    static void perftCase(String name, String fen, long... expected) {
        Pos p = Pos.fromFen(fen);
        for (int i = 0; i < expected.length; i++) {
            long got = perft(p, i + 1);
            boolean ok = got == expected[i];
            if (!ok) fails++;
            System.out.println((ok ? "ok   " : "FAIL ") + name + " depth " + (i + 1) + ": " + got + (ok ? "" : " (expected " + expected[i] + ")"));
        }
    }

    static void check(String name, boolean cond) {
        if (!cond) fails++;
        System.out.println((cond ? "ok   " : "FAIL ") + name);
    }

    public static void main(String[] a) {
        long t0 = System.currentTimeMillis();
        perftCase("startpos", Pos.START_FEN, 20, 400, 8902, 197281);
        perftCase("kiwipete", "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", 48, 2039, 97862);
        perftCase("pos3", "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", 14, 191, 2812, 43238);
        perftCase("pos4", "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1", 6, 264, 9467);
        perftCase("pos5", "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8", 44, 1486, 62379);
        System.out.println("perft time ms: " + (System.currentTimeMillis() - t0));

        // FEN roundtrip
        check("fen roundtrip", Pos.fromFen(Pos.START_FEN).toFen().equals(Pos.START_FEN));
        Pos k = Pos.fromFen("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1");
        check("fen roundtrip kiwipete", k.toFen().equals("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1"));

        // Game: fool's mate
        Game g = new Game();
        for (String m : new String[]{"f2f3", "e7e5", "g2g4", "d8h4"}) check("play " + m, g.playUci(m));
        check("fool's mate result", g.result() == Game.Result.BLACK_WINS && g.reason().equals("Checkmate"));
        check("fool's mate SAN", g.sans().equals(List.of("f3", "e5", "g4", "Qh4#")));
        System.out.println(g.pgn("A", "B"));

        // SAN: disambiguation, castling, promotion, en passant
        Game s = new Game();
        for (String m : new String[]{"e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6", "e1g1", "f6e4"}) check("play " + m, s.playUci(m));
        check("castle SAN", s.sans().get(6).equals("O-O"));
        check("capture SAN", s.sans().get(7).equals("Nxe4"));
        Game e = new Game();
        for (String m : new String[]{"e2e4", "a7a6", "e4e5", "d7d5", "e5d6"}) check("play " + m, e.playUci(m));
        check("en passant SAN", e.sans().get(4).equals("exd6"));
        check("en passant removes pawn", e.pos().b[Pos.sqParse("d5")] == 0);
        Pos pr = Pos.fromFen("8/P6k/8/8/8/8/8/K7 w - - 0 1");
        check("promotion moves", pr.legalMoves().stream().filter(m -> m.from() == Pos.sqParse("a7")).count() == 4);
        Move q = pr.findLegal("a7a8q");
        check("promotion SAN", q != null && Game.san(pr, q, pr.legalMoves()).equals("a8=Q"));
        Pos two = Pos.fromFen("4k3/8/8/8/8/8/4K3/R6R w - - 0 1");
        Move ra = two.findLegal("a1d1");
        check("rook disambiguation", ra != null && Game.san(two, ra, two.legalMoves()).equals("Rad1"));
        Pos rr = Pos.fromFen("4k3/8/8/8/R7/8/8/R3K3 w - - 0 1");
        Move r2 = rr.findLegal("a1a2");
        check("rook disambiguation rank", r2 != null && Game.san(rr, r2, rr.legalMoves()).equals("R1a2"));

        // Draws
        check("stalemate", new Game() != null && Pos.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1").legalMoves().isEmpty() && !Pos.fromFen("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1").inCheck());
        Game rep = new Game();
        String[] shuffle = {"g1f3", "g8f6", "f3g1", "f6g8"};
        for (int i = 0; i < 2; i++) for (String m : shuffle) rep.playUci(m);
        check("threefold repetition", rep.result() == Game.Result.DRAW && rep.reason().equals("Threefold repetition"));

        // Illegal moves rejected
        Game ill = new Game();
        check("reject illegal", !ill.playUci("e2e5") && !ill.playUci("e7e5") && !ill.playUci("zz"));

        // Engine: mate in one, free queen, doesn't hang mate
        Engine eng = new Engine();
        Pos m1 = Pos.fromFen("6k1/5ppp/8/8/8/8/5PPP/R5K1 w - - 0 1");
        Engine.Result r = eng.search(m1, 3, 3000, 0);
        check("engine finds back-rank mate: " + r.move().uci(), r.move().uci().equals("a1a8"));
        Pos hang = Pos.fromFen("rnb1kbnr/pppp1ppp/8/4p3/4P2q/5N2/PPPP1PPP/RNBQKB1R w KQkq - 0 1");
        Engine.Result r2b = eng.search(hang, 3, 3000, 0);
        System.out.println("engine move in position with Nxh4: " + r2b.move().uci() + " score " + r2b.score());
        check("engine takes free queen", r2b.move().uci().equals("f3h4"));
        t0 = System.currentTimeMillis();
        Engine.Result r3 = eng.search(Pos.start(), Level.Hard.depth, Level.Hard.millis, 0);
        System.out.println("startpos hard: " + r3.move().uci() + " depth " + r3.depth() + " nodes " + r3.nodes() + " ms " + (System.currentTimeMillis() - t0));
        Engine.Result r4 = eng.search(k, Level.Expert.depth, 2000, 0);
        System.out.println("kiwipete expert(2s): " + r4.move().uci() + " depth " + r4.depth() + " nodes " + r4.nodes());

        // Engine vs engine short game never produces an illegal move
        Game sim = new Game();
        int plies = 0;
        while (!sim.isOver() && plies < 80) {
            Engine.Result er = eng.search(sim.pos(), 2, 500, 40);
            if (!sim.play(er.move())) { fails++; System.out.println("FAIL engine produced illegal move"); break; }
            plies++;
        }
        System.out.println("sim game " + plies + " plies, result " + sim.result() + " " + sim.reason());
        System.out.println(fails == 0 ? "ALL OK" : ("FAILURES: " + fails));
        System.exit(fails == 0 ? 0 : 1);
    }
}
