package com.rumble5930.chess.gui;

import com.rumble5930.chess.core.Game;
import com.rumble5930.chess.engine.Level;
import com.rumble5930.chess.modules.ChessModule;
import com.rumble5930.chess.net.ChessSession;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WindowScreen;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WView;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.systems.modules.Modules;

import java.util.List;
import java.util.Random;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Chess window: board on the left, game setup / status / controls / move list on the right. */
public class ChessScreen extends WindowScreen {
    private enum SideChoice { White, Black, Random }

    private static final Random RANDOM = new Random();
    private static SideChoice lastSide = SideChoice.White;
    private static String lastName = "";

    private final ChessSession session = ChessSession.get();
    private final Runnable listener = this::requestRefresh;

    private WChessBoard board;
    private WVerticalList dynamic;
    private WDropdown<SideChoice> sideDropdown;
    private WDropdown<Level> levelDropdown;
    private WTextBox nameBox;
    private String error = "";

    public ChessScreen(GuiTheme theme) {
        super(theme, "Chess");
    }

    @Override
    public void initWidgets() {
        WHorizontalList main = add(theme.horizontalList()).expandX().widget();
        board = main.add(new WChessBoard()).top().widget();
        WVerticalList side = main.add(theme.verticalList()).top().minWidth(260).widget();

        // --- new game
        side.add(theme.horizontalSeparator("New game")).expandX();

        WHorizontalList colorRow = side.add(theme.horizontalList()).expandX().widget();
        colorRow.add(theme.label("Play as"));
        sideDropdown = colorRow.add(theme.dropdown(lastSide)).widget();
        sideDropdown.action = () -> lastSide = sideDropdown.get();

        WHorizontalList engineRow = side.add(theme.horizontalList()).expandX().widget();
        engineRow.add(theme.label("Engine"));
        levelDropdown = engineRow.add(theme.dropdown(session.level())).widget();
        WButton play = engineRow.add(theme.button("Play")).expandX().widget();
        play.action = () -> {
            error = "";
            session.startEngineGame(resolveWhite(), levelDropdown.get());
            board.reset();
        };

        // --- online
        side.add(theme.horizontalSeparator("Play a player")).expandX();
        WHorizontalList onlineRow = side.add(theme.horizontalList()).expandX().widget();
        nameBox = onlineRow.add(theme.textBox(lastName, "Player name")).minWidth(120).expandX().widget();
        nameBox.action = () -> lastName = nameBox.get();
        WButton challenge = onlineRow.add(theme.button("Challenge")).widget();
        challenge.action = () -> {
            lastName = nameBox.get();
            String err = session.invite(lastName, resolveWhite());
            error = err == null ? "" : err;
            if (err != null) requestRefresh();
        };

        // --- dynamic area (status, invites, controls, moves)
        dynamic = side.add(theme.verticalList()).expandX().widget();

        session.setListener(listener);
        onClosed(() -> session.clearListener(listener));
        rebuild();
    }

    private boolean resolveWhite() {
        return switch (lastSide) {
            case White -> true;
            case Black -> false;
            case Random -> RANDOM.nextBoolean();
        };
    }

    /** Session changes may arrive in the middle of input handling; rebuild after the current frame. */
    private void requestRefresh() {
        if (dynamic == null) return;
        taskAfterRender = this::rebuild;
    }

    private void rebuild() {
        dynamic.clear();

        Game game = session.game();
        ChessSession.Mode mode = session.mode();

        dynamic.add(theme.horizontalSeparator("Game")).expandX();
        dynamic.add(theme.label(statusText(), 250)).expandX();

        if (mode == ChessSession.Mode.ONLINE || session.outgoing() != null || session.incoming() != null) {
            if (!Modules.get().isActive(ChessModule.class)) {
                dynamic.add(theme.label("The Chess module is disabled - enable it to receive moves and challenges.", 250)
                    .color(theme.textSecondaryColor())).expandX();
            }
        }
        if (!error.isEmpty()) {
            dynamic.add(theme.label(error, 250).color(theme.textSecondaryColor())).expandX();
        } else if (!session.notice().isEmpty()) {
            dynamic.add(theme.label(session.notice(), 250).color(theme.textSecondaryColor())).expandX();
        }

        // incoming challenge
        ChessSession.Invite in = session.incoming();
        if (in != null) {
            dynamic.add(theme.label(in.player() + " challenges you (you play " + (in.inviterWhite() ? "Black" : "White") + ")", 250)).expandX();
            WHorizontalList row = dynamic.add(theme.horizontalList()).expandX().widget();
            WButton accept = row.add(theme.button("Accept")).expandX().widget();
            accept.action = () -> {
                error = "";
                session.acceptIncoming();
                board.reset();
            };
            WButton decline = row.add(theme.button("Decline")).expandX().widget();
            decline.action = session::declineIncoming;
        }

        // outgoing challenge
        ChessSession.Invite out = session.outgoing();
        if (out != null) {
            WHorizontalList row = dynamic.add(theme.horizontalList()).expandX().widget();
            row.add(theme.label("Waiting for " + out.player() + "..."));
            WButton cancel = row.add(theme.button("Cancel")).expandX().widget();
            cancel.action = session::cancelOutgoing;
        }

        // draw offer from the opponent
        if (session.drawOfferedByThem() && !game.isOver()) {
            WHorizontalList row = dynamic.add(theme.horizontalList()).expandX().widget();
            row.add(theme.label("Draw offered"));
            WButton accept = row.add(theme.button("Accept")).expandX().widget();
            accept.action = session::acceptDraw;
            WButton decline = row.add(theme.button("Decline")).expandX().widget();
            decline.action = session::declineDraw;
        }

        // controls
        if (mode != ChessSession.Mode.NONE) {
            WHorizontalList row = dynamic.add(theme.horizontalList()).expandX().widget();
            if (!game.isOver()) {
                row.add(theme.confirmedButton("Resign", "Sure?")).widget().action = session::resign;
                row.add(theme.button("Draw")).widget().action = session::offerDraw;
            }
            row.add(theme.button("Flip")).widget().action = session::flip;

            WHorizontalList copyRow = dynamic.add(theme.horizontalList()).expandX().widget();
            copyRow.add(theme.button("Copy PGN")).widget().action = () -> {
                mc.keyboardHandler.setClipboard(session.pgn());
                error = "";
            };
            copyRow.add(theme.button("Copy FEN")).widget().action = () -> mc.keyboardHandler.setClipboard(session.fen());
        }

        // move list
        List<String> sans = game.sans();
        if (!sans.isEmpty()) {
            dynamic.add(theme.horizontalSeparator("Moves")).expandX();
            WView view = dynamic.add(theme.view()).expandX().widget();
            view.maxHeight = theme.scale(170);
            WTable table = view.add(theme.table()).expandX().widget();
            for (int i = 0; i < sans.size(); i += 2) {
                table.add(theme.label((i / 2 + 1) + ".").color(theme.textSecondaryColor()));
                table.add(theme.label(sans.get(i)));
                if (i + 1 < sans.size()) table.add(theme.label(sans.get(i + 1)));
                table.row();
            }
        }
    }

    private String statusText() {
        Game game = session.game();
        ChessSession.Mode mode = session.mode();
        if (mode == ChessSession.Mode.NONE) return "No game running. Start one above.";

        String versus = mode == ChessSession.Mode.ENGINE ? "the engine (" + session.level() + ")" : session.opponent();
        String you = session.playerWhite() ? "White" : "Black";

        if (game.isOver()) {
            String winner = switch (game.result()) {
                case WHITE_WINS -> "White wins";
                case BLACK_WINS -> "Black wins";
                default -> "Draw";
            };
            boolean won = (game.result() == Game.Result.WHITE_WINS && session.playerWhite())
                || (game.result() == Game.Result.BLACK_WINS && !session.playerWhite());
            String outcome = game.result() == Game.Result.DRAW ? "" : (won ? " - you won!" : " - you lost.");
            return game.reason() + ": " + winner + outcome;
        }

        String turn;
        if (session.isMyTurn()) turn = "Your turn";
        else if (session.thinking()) turn = "The engine is thinking...";
        else turn = "Waiting for " + (mode == ChessSession.Mode.ENGINE ? "the engine" : session.opponent()) + "...";
        if (game.pos().inCheck()) turn += " (check!)";
        return "You play " + you + " against " + versus + ". " + turn;
    }
}
