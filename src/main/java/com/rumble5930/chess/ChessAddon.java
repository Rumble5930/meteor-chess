package com.rumble5930.chess;

import com.rumble5930.chess.commands.ChessCommand;
import com.rumble5930.chess.modules.ChessModule;
import com.rumble5930.chess.modules.MeteorPlatform;
import com.rumble5930.chess.net.ChessSession;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.commands.Commands;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class ChessAddon extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Chess");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Meteor Chess");

        ChessSession.install(new MeteorPlatform(() -> {
            ChessModule module = Modules.get().get(ChessModule.class);
            return module != null ? module.whisperCommand.get() : "w";
        }));

        Modules.get().add(new ChessModule());
        Commands.add(new ChessCommand());
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.rumble5930.chess";
    }
}
