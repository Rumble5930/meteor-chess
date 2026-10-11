package com.rumble5930.chess.net;

import java.util.List;

/** What the chess session needs from the game client. Implemented for Meteor in the modules package. */
public interface Platform {
    /** Sends text privately to a player (whisper). */
    void whisper(String player, String text);

    /** Shows an informational message to the user (chat). */
    void info(String message);

    /** Runs a task on the client main thread. */
    void runOnMain(Runnable task);

    /** The local player's name. */
    String ownName();

    /** Names of players currently known to be online (tab list). May be empty. */
    List<String> onlineNames();
}
