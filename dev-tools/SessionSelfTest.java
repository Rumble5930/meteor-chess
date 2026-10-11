import com.rumble5930.chess.core.*;
import com.rumble5930.chess.engine.Level;
import com.rumble5930.chess.net.*;
import java.util.*;

public class SessionSelfTest {
    static int fails = 0;
    static final Map<String, ChessSession> sessions = new HashMap<>();
    static final Deque<Runnable> main = new ArrayDeque<>();
    static final List<String> wire = new ArrayList<>();

    static class P implements Platform {
        final String me;
        final List<String> infos = new ArrayList<>();
        P(String me) { this.me = me; }
        public void whisper(String player, String text) {
            wire.add(me + " -> " + player + ": " + text);
            // sender sees an echo, recipient sees the whisper; delivered via the main queue (no reentrancy)
            main.add(() -> sessions.get(me).onChat("You whisper to " + player + ": " + text));
            main.add(() -> { ChessSession s = sessions.get(player); if (s != null) s.onChat(me + " whispers to you: " + text); });
        }
        public void info(String message) { infos.add(message); }
        public void runOnMain(Runnable task) { synchronized (main) { main.add(task); } }
        public String ownName() { return me; }
        public List<String> onlineNames() { return List.of("Alice", "Bob", "Eve"); }
    }

    static void check(String name, boolean cond) {
        if (!cond) fails++;
        System.out.println((cond ? "ok   " : "FAIL ") + name);
    }

    static void drain() {
        for (int i = 0; i < 100000; i++) {
            Runnable r;
            synchronized (main) { r = main.poll(); }
            if (r == null) return;
            r.run();
        }
    }

    static boolean play(ChessSession s, String uci) {
        boolean ok = s.tryMove(s.game().pos().findLegal(uci));
        drain();
        return ok;
    }

    public static void main(String[] a) throws Exception {
        ChessSession alice = new ChessSession(new P("Alice"));
        ChessSession bob = new ChessSession(new P("Bob"));
        sessions.put("Alice", alice);
        sessions.put("Bob", bob);

        // --- challenge / accept
        check("invite ok", alice.invite("Bob", true) == null);
        drain();
        check("bob has incoming", bob.incoming() != null && bob.incoming().player().equals("Alice") && bob.incoming().inviterWhite());
        check("alice ignores own echo", alice.incoming() == null && alice.outgoing() != null);
        bob.acceptIncoming();
        drain();
        check("online game on both", alice.mode() == ChessSession.Mode.ONLINE && bob.mode() == ChessSession.Mode.ONLINE);
        check("colours", alice.playerWhite() && !bob.playerWhite());
        check("same game id", alice.game() != bob.game() && alice.opponent().equals("Bob") && bob.opponent().equals("Alice"));

        // --- moves, turn enforcement
        check("bob can't move first", !bob.tryMove(bob.game().pos().findLegal("e7e5")));
        for (String m : new String[]{"e2e4", "e7e5", "f1c4", "b8c6", "d1h5", "g8f6", "h5f7"}) {
            ChessSession mover = alice.isMyTurn() ? alice : bob;
            check("move " + m, play(mover, m));
        }
        check("scholar's mate seen by both", alice.game().result() == Game.Result.WHITE_WINS && bob.game().result() == Game.Result.WHITE_WINS);
        check("same SAN history", alice.game().sans().equals(bob.game().sans()));
        System.out.println(alice.pgn());

        // --- spoofing: Eve whispers a valid-looking move tag in a running game
        ChessSession a2 = new ChessSession(new P("Alice"));
        ChessSession b2 = new ChessSession(new P("Bob"));
        sessions.put("Alice", a2);
        sessions.put("Bob", b2);
        a2.invite("Bob", false);
        drain();
        b2.acceptIncoming();
        drain();
        check("a2 plays black", !a2.playerWhite());
        String id = a2.opponent().isEmpty() ? "" : wire.get(wire.size() - 1).replaceAll(".*mchess:v1:([a-z0-9]{6}):.*", "$1");
        a2.onChat("Eve whispers to you: " + Protocol.build(id, true, "mov", "0-e2e4"));
        check("spoofed move ignored", a2.game().plyCount() == 0);
        b2.onChat("Eve whispers to you: " + Protocol.build(id, false, "rsg", ""));
        check("spoofed resign ignored", !b2.game().isOver());
        b2.onChat("Alice whispers to you: " + Protocol.build(id, false, "mov", "5-e7e5"));
        check("out-of-sync ignored", b2.game().plyCount() == 0);
        // illegal move from the real opponent is ignored
        play(b2, "e2e4");
        a2.onChat("Bob whispers to you: " + Protocol.build(id, true, "mov", "0-e2e4"));
        check("duplicate ignored", a2.game().plyCount() == 1);
        a2.onChat("Bob whispers to you: " + Protocol.build(id, true, "mov", "1-e2e4"));
        check("illegal ignored", a2.game().plyCount() == 1);

        // --- draw offer and resign
        b2.offerDraw();
        drain();
        check("draw offer seen", a2.drawOfferedByThem() && b2.drawOfferedByMe());
        a2.acceptDraw();
        drain();
        check("draw agreed on both", a2.game().result() == Game.Result.DRAW && b2.game().result() == Game.Result.DRAW);

        ChessSession a3 = new ChessSession(new P("Alice"));
        ChessSession b3 = new ChessSession(new P("Bob"));
        sessions.put("Alice", a3);
        sessions.put("Bob", b3);
        b3.invite("Alice", true);
        drain();
        a3.declineIncoming();
        drain();
        check("decline clears outgoing", b3.outgoing() == null && b3.mode() == ChessSession.Mode.NONE);
        a3.invite("Bob", true);
        drain();
        b3.acceptIncoming();
        drain();
        a3.resign();
        drain();
        check("resign propagates", b3.game().result() == Game.Result.WHITE_WINS == false && b3.game().result() == Game.Result.BLACK_WINS);

        // --- engine game
        ChessSession solo = new ChessSession(new P("Alice"));
        sessions.put("Alice", solo);
        solo.startEngineGame(false, Level.Easy);   // player is black -> engine moves first
        long end = System.currentTimeMillis() + 5000;
        while (solo.game().plyCount() == 0 && System.currentTimeMillis() < end) { drain(); Thread.sleep(10); }
        check("engine opened as white", solo.game().plyCount() == 1 && solo.isMyTurn());
        Move reply = solo.game().pos().legalMoves().get(0);
        check("player move accepted", solo.tryMove(reply));
        end = System.currentTimeMillis() + 5000;
        while (solo.game().plyCount() < 3 && System.currentTimeMillis() < end) { drain(); Thread.sleep(10); }
        check("engine replied", solo.game().plyCount() == 3);
        // starting a new game while engine thinks must discard the old result
        solo.tryMove(solo.game().pos().legalMoves().get(0));
        solo.startEngineGame(true, Level.Beginner);
        Thread.sleep(800); drain();
        check("stale engine result discarded", solo.game().plyCount() == 0 && solo.isMyTurn());

        System.out.println(fails == 0 ? "ALL OK" : "FAILURES: " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }
}
