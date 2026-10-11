# Meteor Chess

A Meteor Client addon (Minecraft 26.2) that adds a chess board to Meteor's GUI.

Author: Rumble5930

- **Versus the computer**: built-in engine (alpha-beta search, 5 levels: Beginner, Easy, Medium, Hard, Expert) with no external dependencies.
- **Versus other players**: challenges and moves are exchanged by whisper between players who have the module (whisper command is configurable, default `/w`).
- **GUI**: a Meteor widget that uses the colors of the active theme. Click a piece, then a destination square. Includes promotion picker, last-move and check highlights, legal-move hints, move list, and copy PGN/FEN.

## Usage
1. Enable the **chess** module (Chess category). It must be enabled to receive challenges and moves.
2. Open the board with the button in the module settings, the `open-board` keybind, or `.chess`.
3. Commands: `.chess challenge <player> [white|black]`, `.chess accept`, `.chess decline`, `.chess resign`.

## Protocol
Messages look like `[mchess:v1:<id>:<w|b>:<type>:<payload>]`, are sent as whispers, and are hidden from chat (`hide-chess-messages` setting).
Every received move is validated locally. A message is accepted only if the text before the tag contains the opponent's name and the game id matches.

## Build
`./gradlew build` (JDK 25). Versions are in `gradle/libs.versions.toml` (official Meteor addon template, 26.2).

## Core tests (no Minecraft needed)
```
javac -d /tmp/out src/main/java/com/rumble5930/chess/{core,engine,net}/*.java dev-tools/*.java
java -cp /tmp/out CoreSelfTest
java -cp /tmp/out SessionSelfTest
```
`dev-tools/gen_pieces.py` regenerates the piece sprites.
