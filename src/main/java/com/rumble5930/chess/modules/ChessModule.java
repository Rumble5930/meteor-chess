package com.rumble5930.chess.modules;

import com.rumble5930.chess.ChessAddon;
import com.rumble5930.chess.gui.ChessScreen;
import com.rumble5930.chess.net.ChessSession;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.orbit.EventPriority;

/**
 * Listens for chess messages (invites and moves sent through whispers) while enabled.
 * The board itself is opened with the keybind below, the button in this module's settings, or {@code .chess}.
 */
public class ChessModule extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    public final Setting<String> whisperCommand = sgGeneral.add(new StringSetting.Builder()
        .name("whisper-command")
        .description("Command used to send private messages, without the slash (w, msg, tell...).")
        .defaultValue("w")
        .build()
    );

    private final Setting<Boolean> hideMessages = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-chess-messages")
        .description("Hides the technical chess messages from your chat.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> openOnChallenge = sgGeneral.add(new BoolSetting.Builder()
        .name("open-on-challenge")
        .description("Opens the board when someone challenges you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Keybind> openKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("open-board")
        .description("Opens the chess board (works while the module is enabled).")
        .defaultValue(Keybind.none())
        .action(this::openBoard)
        .build()
    );

    public ChessModule() {
        super(ChessAddon.CATEGORY, "chess", "Play chess against an engine or against other players who use this addon.");
        ChessSession.get().inviteHook = _ -> {
            if (openOnChallenge.get()) openBoard();
        };
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WButton open = theme.button("Open board");
        open.action = this::openBoard;
        return open;
    }

    public void openBoard() {
        if (mc.gui.screen() instanceof ChessScreen) return;
        mc.gui.setScreen(new ChessScreen(GuiThemes.get()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onMessage(ReceiveMessageEvent event) {
        boolean chess = ChessSession.get().onChat(event.getMessage().getString());
        if (chess && hideMessages.get()) event.cancel();
    }
}
