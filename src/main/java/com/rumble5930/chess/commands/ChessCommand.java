package com.rumble5930.chess.commands;

import com.rumble5930.chess.gui.ChessScreen;
import com.rumble5930.chess.modules.ChessModule;
import com.rumble5930.chess.net.ChessSession;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import meteordevelopment.meteorclient.commands.Command;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;

/** {@code .chess}, {@code .chess challenge <player> [white|black]}, {@code .chess accept|decline|resign}. */
public class ChessCommand extends Command {
    public ChessCommand() {
        super("chess", "Opens the chess board and manages online games.");
    }

    @Override
    public void build(LiteralArgumentBuilder<ClientSuggestionProvider> builder) {
        builder.executes(_ -> {
            open();
            return SINGLE_SUCCESS;
        });

        builder.then(literal("challenge")
            .then(argument("player", StringArgumentType.word())
                .executes(ctx -> challenge(ctx, true))
                .then(literal("white").executes(ctx -> challenge(ctx, true)))
                .then(literal("black").executes(ctx -> challenge(ctx, false)))));

        builder.then(literal("accept").executes(_ -> {
            if (ChessSession.get().incoming() == null) error("No pending challenge.");
            else ChessSession.get().acceptIncoming();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("decline").executes(_ -> {
            if (ChessSession.get().incoming() == null) error("No pending challenge.");
            else ChessSession.get().declineIncoming();
            return SINGLE_SUCCESS;
        }));

        builder.then(literal("resign").executes(_ -> {
            ChessSession.get().resign();
            return SINGLE_SUCCESS;
        }));
    }

    private void open() {
        ChessScreen screen = new ChessScreen(GuiThemes.get());
        screen.parent = null; // the chat screen is about to close; don't return to it
        Utils.screenToOpen = screen;
    }

    private int challenge(CommandContext<ClientSuggestionProvider> ctx, boolean white) {
        if (!Modules.get().isActive(ChessModule.class)) {
            error("Enable the chess module first, otherwise you won't receive the answer.");
            return SINGLE_SUCCESS;
        }
        String err = ChessSession.get().invite(StringArgumentType.getString(ctx, "player"), white);
        if (err != null) error(err);
        return SINGLE_SUCCESS;
    }
}
