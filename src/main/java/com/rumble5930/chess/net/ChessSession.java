package com.rumble5930.chess.net;

import com.rumble5930.chess.core.Game;
import com.rumble5930.chess.core.Move;
import com.rumble5930.chess.core.Pos;
import com.rumble5930.chess.engine.Engine;
import com.rumble5930.chess.engine.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * All chess state that must survive closing the GUI: the current game, engine or online mode, pending invites
 * and draw offers. Every method must be called from the client main thread (engine results are marshalled back
 * through {@link Platform#runOnMain}).
 */
public final class ChessSession {
    public enum Mode { NONE, ENGINE, ONLINE }

    public record Invite(String player, String gameId, boolean inviterWhite) {}

    private static final Pattern TOKEN = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Set<String> STOP_WORDS = Set.of(
        "you", "to", "from", "whispers", "whisper", "tell", "tells", "msg", "pm", "says", "me", "of", "the", "dm", "w", "chess");

    private static ChessSession instance;

    public static void install(Platform platform) {
        instance = new ChessSession(platform);
    }

    public static ChessSession get() {
        if (instance == null) throw new IllegalStateException("ChessSession not installed");
        return instance;
    }

    private final Platform platform;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "meteor-chess-engine");
        t.setDaemon(true);
        return t;
    });

    private Mode mode = Mode.NONE;
    private Game game = new Game();
    private boolean playerWhite = true;
    private boolean flipped;
    private Level level = Level.Medium;
    private String opponent = "";
    private String gameId = "";
    private Invite incoming;
    private Invite outgoing;
    private boolean drawOfferedByMe;
    private boolean drawOfferedByThem;
    private boolean thinking;
    private String notice = "";

    private Engine currentEngine;
    private int searchToken;
    private Runnable listener;

    /** Called when an invite arrives (the module may open the GUI from here). */
    public Consumer<Invite> inviteHook;

    public ChessSession(Platform platform) {
        this.platform = platform;
    }

    // ---------------------------------------------------------------- state access

    public Mode mode() { return mode; }
    public Game game() { return game; }
    public boolean playerWhite() { return playerWhite; }
    public boolean flipped() { return flipped; }
    public Level level() { return level; }
    public String opponent() { return opponent; }
    public Invite incoming() { return incoming; }
    public Invite outgoing() { return outgoing; }
    public boolean drawOfferedByMe() { return drawOfferedByMe; }
    public boolean drawOfferedByThem() { return drawOfferedByThem; }
    public boolean thinking() { return thinking; }
    public String notice() { return notice; }

    /** True when the board should be shown from White's side. */
    public boolean whiteAtBottom() {
        return playerWhite != flipped;
    }

    public boolean isMyTurn() {
        return mode != Mode.NONE && !game.isOver() && game.pos().whiteToMove == playerWhite;
    }

    public List<Move> movesFrom(int sq) {
        List<Move> out = new ArrayList<>();
        for (Move m : game.pos().legalMoves()) if (m.from() == sq) out.add(m);
        return out;
    }

    public void setListener(Runnable r) { this.listener = r; }

    public void clearListener(Runnable r) {
        if (listener == r) listener = null;
    }

    public void flip() {
        flipped = !flipped;
        changed();
    }

    private void changed() {
        if (listener != null) listener.run();
    }

    private void say(String message) {
        notice = message;
        platform.info(message);
    }

    // ---------------------------------------------------------------- starting games

    public void startEngineGame(boolean white, Level level) {
        leaveOnlineGame();
        cancelSearch();
        outgoing = null;
        this.mode = Mode.ENGINE;
        this.level = level;
        this.playerWhite = white;
        this.opponent = "";
        this.gameId = "";
        this.game = new Game();
        this.flipped = false;
        this.drawOfferedByMe = false;
        this.drawOfferedByThem = false;
        this.notice = "";
        changed();
        maybeEngineMove();
    }

    /** Challenges a player. Returns an error message, or null on success. */
    public String invite(String name, boolean iAmWhite) {
        name = name == null ? "" : name.trim();
        if (!Protocol.validName(name)) return "Enter a valid player name";
        if (name.equalsIgnoreCase(platform.ownName())) return "You can't challenge yourself";
        if (mode == Mode.ONLINE && !game.isOver()) return "Finish your online game first";

        String id = Protocol.newId();
        outgoing = new Invite(name, id, iAmWhite);
        platform.whisper(name, Protocol.build(id, iAmWhite, "inv", ""));
        notice = "Challenge sent to " + name + ". Waiting for an answer...";
        changed();
        return null;
    }

    public void cancelOutgoing() {
        if (outgoing == null) return;
        outgoing = null;
        notice = "Challenge cancelled";
        changed();
    }

    public void acceptIncoming() {
        Invite inv = incoming;
        if (inv == null) return;
        if (mode == Mode.ONLINE && !game.isOver()) {
            notice = "Finish your online game first";
            changed();
            return;
        }
        incoming = null;
        cancelSearch();
        outgoing = null;
        mode = Mode.ONLINE;
        opponent = inv.player();
        gameId = inv.gameId();
        playerWhite = !inv.inviterWhite();
        game = new Game();
        flipped = false;
        drawOfferedByMe = false;
        drawOfferedByThem = false;
        notice = "Game started against " + opponent + " (you play " + (playerWhite ? "White" : "Black") + ")";
        platform.whisper(opponent, Protocol.build(gameId, playerWhite, "acc", ""));
        changed();
    }

    public void declineIncoming() {
        Invite inv = incoming;
        if (inv == null) return;
        incoming = null;
        platform.whisper(inv.player(), Protocol.build(inv.gameId(), !inv.inviterWhite(), "dec", ""));
        notice = "Declined challenge from " + inv.player();
        changed();
    }

    private void leaveOnlineGame() {
        if (mode == Mode.ONLINE && !game.isOver()) {
            game.resign(playerWhite);
            platform.whisper(opponent, Protocol.build(gameId, playerWhite, "rsg", ""));
        }
    }

    // ---------------------------------------------------------------- playing

    public boolean tryMove(Move m) {
        if (!isMyTurn()) return false;
        if (!game.pos().legalMoves().contains(m)) return false;
        game.play(m);
        drawOfferedByMe = false;
        drawOfferedByThem = false;
        notice = "";
        if (mode == Mode.ONLINE) {
            platform.whisper(opponent, Protocol.build(gameId, playerWhite, "mov", (game.plyCount() - 1) + "-" + m.uci()));
        }
        changed();
        maybeEngineMove();
        return true;
    }

    public void resign() {
        if (mode == Mode.NONE || game.isOver()) return;
        game.resign(playerWhite);
        if (mode == Mode.ONLINE) platform.whisper(opponent, Protocol.build(gameId, playerWhite, "rsg", ""));
        changed();
    }

    public void offerDraw() {
        if (mode == Mode.NONE || game.isOver()) return;
        if (mode == Mode.ENGINE) {
            int engineScore = Engine.eval(game.pos());
            if (game.pos().whiteToMove == playerWhite) engineScore = -engineScore;
            boolean accept = engineScore <= -50 || (Math.abs(engineScore) < 50 && game.plyCount() >= 40);
            if (accept) {
                game.drawByAgreement();
                notice = "The engine accepted the draw";
            } else {
                notice = "The engine declined the draw";
            }
            changed();
            return;
        }
        if (drawOfferedByThem) {
            acceptDraw();
            return;
        }
        drawOfferedByMe = true;
        platform.whisper(opponent, Protocol.build(gameId, playerWhite, "dro", ""));
        notice = "Draw offered";
        changed();
    }

    public void acceptDraw() {
        if (mode != Mode.ONLINE || !drawOfferedByThem || game.isOver()) return;
        game.drawByAgreement();
        drawOfferedByThem = false;
        platform.whisper(opponent, Protocol.build(gameId, playerWhite, "dra", ""));
        changed();
    }

    public void declineDraw() {
        if (mode != Mode.ONLINE || !drawOfferedByThem) return;
        drawOfferedByThem = false;
        platform.whisper(opponent, Protocol.build(gameId, playerWhite, "drd", ""));
        notice = "Draw declined";
        changed();
    }

    // ---------------------------------------------------------------- engine

    private void cancelSearch() {
        searchToken++;
        thinking = false;
        if (currentEngine != null) currentEngine.cancel();
    }

    private void maybeEngineMove() {
        if (mode != Mode.ENGINE || game.isOver() || game.pos().whiteToMove == playerWhite) return;

        final int token = ++searchToken;
        final Game g = game;
        final Pos pos = game.pos();
        final Level lv = level;
        final Engine engine = new Engine();
        if (currentEngine != null) currentEngine.cancel();
        currentEngine = engine;
        thinking = true;
        changed();

        executor.submit(() -> {
            Engine.Result r = null;
            try {
                r = engine.search(pos, lv.depth, lv.millis, lv.noise);
            } catch (RuntimeException e) {
                e.printStackTrace();
            }
            final Engine.Result result = r;
            platform.runOnMain(() -> {
                if (token != searchToken || g != game) return;
                thinking = false;
                if (result != null) g.play(result.move());
                changed();
            });
        });
    }

    // ---------------------------------------------------------------- incoming chat

    /** Processes a chat line. Returns true if it carried a chess message (the caller may hide it). */
    public boolean onChat(String text) {
        Protocol.Parsed p = Protocol.parse(text);
        if (p == null) return false;
        handle(p.msg(), p.prefix());
        return true;
    }

    private static boolean prefixHas(String prefix, String name) {
        return !name.isEmpty() && prefix.toLowerCase(Locale.ROOT).contains(name.toLowerCase(Locale.ROOT));
    }

    private String senderName(String prefix) {
        String own = platform.ownName();
        List<String> online = platform.onlineNames();
        List<String> tokens = new ArrayList<>();
        Matcher m = TOKEN.matcher(prefix);
        while (m.find()) tokens.add(m.group());

        for (String t : tokens) {
            if (t.equalsIgnoreCase(own)) continue;
            for (String o : online) if (o.equalsIgnoreCase(t)) return o;
        }
        for (String t : tokens) {
            if (t.equalsIgnoreCase(own) || STOP_WORDS.contains(t.toLowerCase(Locale.ROOT))) continue;
            if (Protocol.validName(t)) return t;
        }
        return null;
    }

    private void handle(Protocol.Msg m, String prefix) {
        switch (m.type()) {
            case "inv" -> onInvite(m, prefix);
            case "acc" -> {
                Invite out = outgoing;
                if (out == null || !out.gameId().equals(m.gameId())) return;
                if (m.senderWhite() == out.inviterWhite()) return; // our own echo
                if (!prefixHas(prefix, out.player())) return;
                outgoing = null;
                cancelSearch();
                mode = Mode.ONLINE;
                opponent = out.player();
                gameId = out.gameId();
                playerWhite = out.inviterWhite();
                game = new Game();
                flipped = false;
                drawOfferedByMe = false;
                drawOfferedByThem = false;
                say(opponent + " accepted. You play " + (playerWhite ? "White" : "Black") + ".");
                changed();
            }
            case "dec" -> {
                Invite out = outgoing;
                if (out == null || !out.gameId().equals(m.gameId())) return;
                if (m.senderWhite() == out.inviterWhite()) return;
                if (!prefixHas(prefix, out.player())) return;
                outgoing = null;
                say(out.player() + " declined the challenge");
                changed();
            }
            default -> onGameMessage(m, prefix);
        }
    }

    private void onInvite(Protocol.Msg m, String prefix) {
        if (outgoing != null && outgoing.gameId().equals(m.gameId())) return; // echo of our own invite
        if (incoming != null && incoming.gameId().equals(m.gameId())) return; // duplicate
        String sender = senderName(prefix);
        if (sender == null) return;
        if ((mode == Mode.ONLINE && !game.isOver()) || incoming != null) {
            platform.whisper(sender, Protocol.build(m.gameId(), !m.senderWhite(), "dec", "busy"));
            return;
        }
        incoming = new Invite(sender, m.gameId(), m.senderWhite());
        say(sender + " challenges you to a game of chess (you would play " + (m.senderWhite() ? "Black" : "White") + ")");
        changed();
        if (inviteHook != null) inviteHook.accept(incoming);
    }

    private void onGameMessage(Protocol.Msg m, String prefix) {
        if (mode != Mode.ONLINE || !gameId.equals(m.gameId())) return;
        if (m.senderWhite() == playerWhite) return; // our own echo
        if (!prefixHas(prefix, opponent)) return;

        switch (m.type()) {
            case "mov" -> {
                if (game.isOver()) return;
                int dash = m.payload().indexOf('-');
                if (dash <= 0) return;
                int ply;
                try {
                    ply = Integer.parseInt(m.payload().substring(0, dash));
                } catch (NumberFormatException e) {
                    return;
                }
                if (ply < game.plyCount()) return; // duplicate / old
                if (ply > game.plyCount()) {
                    say("Out of sync with " + opponent + " (a move was lost)");
                    changed();
                    return;
                }
                Move mv = game.pos().findLegal(m.payload().substring(dash + 1));
                if (mv == null || game.pos().whiteToMove != m.senderWhite()) {
                    say(opponent + " sent an illegal move - ignored");
                    changed();
                    return;
                }
                game.play(mv);
                drawOfferedByMe = false;
                drawOfferedByThem = false;
                notice = "";
                changed();
            }
            case "rsg" -> {
                if (game.isOver()) return;
                game.resign(!playerWhite);
                say(opponent + " resigned");
                changed();
            }
            case "dro" -> {
                if (game.isOver()) return;
                drawOfferedByThem = true;
                say(opponent + " offers a draw");
                changed();
            }
            case "dra" -> {
                if (!drawOfferedByMe || game.isOver()) return;
                drawOfferedByMe = false;
                game.drawByAgreement();
                say(opponent + " accepted the draw");
                changed();
            }
            case "drd" -> {
                if (!drawOfferedByMe) return;
                drawOfferedByMe = false;
                say(opponent + " declined the draw");
                changed();
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------- export

    public String pgn() {
        String me = platform.ownName();
        String other = mode == Mode.ONLINE ? opponent : "Engine (" + level + ")";
        return playerWhite ? game.pgn(me, other) : game.pgn(other, me);
    }

    public String fen() {
        return game.pos().toFen();
    }
}
