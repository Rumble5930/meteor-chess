package com.rumble5930.chess.engine;

/** Engine strength presets: search depth, random noise (centipawns, makes it weaker/less predictable) and time cap. */
public enum Level {
    Beginner(1, 220, 400),
    Easy(2, 90, 800),
    Medium(3, 25, 2000),
    Hard(5, 0, 4000),
    Expert(7, 0, 7000);

    public final int depth;
    public final int noise;
    public final long millis;

    Level(int depth, int noise, long millis) {
        this.depth = depth;
        this.noise = noise;
        this.millis = millis;
    }
}
