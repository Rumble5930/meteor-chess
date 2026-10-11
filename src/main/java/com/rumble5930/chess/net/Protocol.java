package com.rumble5930.chess.net;

import java.security.SecureRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Wire format for chess messages exchanged through private chat (whispers):
 * {@code [mchess:v1:<gameId>:<w|b sender colour>:<type>:<payload>]}.
 * Types: inv (invite), acc (accept), dec (decline), mov ("ply-uci"), rsg (resign),
 * dro / dra / drd (draw offer / accept / decline).
 */
public final class Protocol {
    public record Msg(String gameId, boolean senderWhite, String type, String payload) {}

    /** A parsed message plus the chat text that preceded the tag (used to identify the sender). */
    public record Parsed(Msg msg, String prefix) {}

    private static final Pattern TAG =
        Pattern.compile("\\[mchess:v1:([a-z0-9]{6}):([wb]):([a-z]{3}):([A-Za-z0-9_-]{0,24})]");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final String ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RNG = new SecureRandom();

    private Protocol() {}

    public static String newId() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) sb.append(ALPHABET.charAt(RNG.nextInt(ALPHABET.length())));
        return sb.toString();
    }

    public static String build(String gameId, boolean senderWhite, String type, String payload) {
        return "[mchess:v1:" + gameId + ":" + (senderWhite ? "w" : "b") + ":" + type + ":" + payload + "]";
    }

    /** Returns null if the text contains no chess tag. */
    public static Parsed parse(String text) {
        if (text == null) return null;
        Matcher m = TAG.matcher(text);
        if (!m.find()) return null;
        return new Parsed(new Msg(m.group(1), m.group(2).equals("w"), m.group(3), m.group(4)), text.substring(0, m.start()));
    }

    public static boolean validName(String name) {
        return name != null && NAME.matcher(name).matches();
    }
}
