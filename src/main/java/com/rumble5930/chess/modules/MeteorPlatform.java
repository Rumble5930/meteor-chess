package com.rumble5930.chess.modules;

import com.rumble5930.chess.net.Platform;
import meteordevelopment.meteorclient.utils.player.ChatUtils;

import java.util.List;
import java.util.function.Supplier;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/** Connects the chess session to Meteor / Minecraft. */
public class MeteorPlatform implements Platform {
    private final Supplier<String> whisperCommand;

    public MeteorPlatform(Supplier<String> whisperCommand) {
        this.whisperCommand = whisperCommand;
    }

    @Override
    public void whisper(String player, String text) {
        if (mc.player == null) return;
        String command = whisperCommand.get().trim();
        if (command.startsWith("/")) command = command.substring(1);
        ChatUtils.sendPlayerMsg("/" + command + " " + player + " " + text, false);
    }

    @Override
    public void info(String message) {
        ChatUtils.infoPrefix("Chess", message.replace("%", "%%"));
    }

    @Override
    public void runOnMain(Runnable task) {
        mc.execute(task);
    }

    @Override
    public String ownName() {
        return mc.getUser().getName();
    }

    @Override
    public List<String> onlineNames() {
        if (mc.getConnection() == null) return List.of();
        return mc.getConnection().getOnlinePlayers().stream().map(e -> e.getProfile().name()).toList();
    }
}
