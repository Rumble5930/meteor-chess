package com.rumble5930.chess.gui;

import com.rumble5930.chess.core.Move;
import com.rumble5930.chess.core.Pos;
import com.rumble5930.chess.net.ChessSession;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.renderer.Texture;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.input.MouseButtonEvent;

import java.util.ArrayList;
import java.util.List;

import static com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT;
import static com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT;

/**
 * The chess board widget. Squares, highlights and coordinates are drawn with Meteor's GuiRenderer using colours
 * taken from the active GUI theme (accent / outline / text), so it blends in with the rest of the client.
 * Click a piece, then a highlighted square, to move.
 */
public class WChessBoard extends WWidget {
    private static final int[] PROMO_ORDER = {Pos.Q, Pos.R, Pos.B, Pos.N};

    private static final Color LIGHT_SQUARE = new Color(98, 98, 108, 235);
    private static final Color DARK_SQUARE = new Color(42, 42, 50, 235);
    private static final Color CHECK = new Color(225, 55, 55, 170);
    private static final Color HOVER = new Color(255, 255, 255, 32);
    private static final Color PROMO_BG = new Color(28, 28, 34, 250);

    private int selected = -1;
    private int selectedPly = -1;
    private int promoFrom = -1;
    private int promoTo = -1;
    private boolean promoWhite = true;
    private double mx, my;

    public void reset() {
        selected = -1;
        promoFrom = -1;
    }

    // ---------------------------------------------------------------- geometry

    private double cell() {
        return theme.scale(46);
    }

    private double gutter() {
        return theme.textHeight() + theme.scale(6);
    }

    private double topPad() {
        return theme.scale(4);
    }

    private double originX() {
        return x + gutter();
    }

    private double originY() {
        return y + topPad();
    }

    @Override
    protected void onCalculateSize() {
        width = gutter() + 8 * cell() + topPad();
        height = topPad() + 8 * cell() + gutter();
    }

    private static int toSquare(int col, int row, boolean whiteBottom) {
        int file = whiteBottom ? col : 7 - col;
        int rank = whiteBottom ? 7 - row : row;
        return rank * 8 + file;
    }

    private int squareAt(double px, double py, boolean whiteBottom) {
        double cell = cell();
        double fx = (px - originX()) / cell;
        double fy = (py - originY()) / cell;
        if (fx < 0 || fy < 0 || fx >= 8 || fy >= 8) return -1;
        return toSquare((int) fx, (int) fy, whiteBottom);
    }

    // Promotion picker: a strip of four squares starting at the promotion square and going inwards.

    private int promoCol(boolean wb) {
        int file = promoTo & 7;
        return wb ? file : 7 - file;
    }

    private int promoRow0(boolean wb) {
        int rank = promoTo >> 3;
        return wb ? 7 - rank : rank;
    }

    private int promoDir(boolean wb) {
        return promoRow0(wb) == 0 ? 1 : -1;
    }

    private boolean inPromoStrip(int col, int row, boolean wb) {
        if (promoFrom < 0 || col != promoCol(wb)) return false;
        int i = (row - promoRow0(wb)) * promoDir(wb);
        return i >= 0 && i < 4;
    }

    private int promoIndexAt(double px, double py, boolean wb) {
        double cell = cell();
        double fx = (px - originX()) / cell;
        double fy = (py - originY()) / cell;
        if (fx < 0 || fy < 0 || fx >= 8 || fy >= 8) return -1;
        int col = (int) fx, row = (int) fy;
        if (col != promoCol(wb)) return -1;
        int i = (row - promoRow0(wb)) * promoDir(wb);
        return i >= 0 && i < 4 ? i : -1;
    }

    // ---------------------------------------------------------------- colours from the theme

    private Color accent(int alpha) {
        if (theme instanceof MeteorGuiTheme mt) {
            Color c = mt.accentColor.get();
            return new Color(c.r, c.g, c.b, alpha);
        }
        return new Color(145, 61, 226, alpha);
    }

    private Color outline() {
        if (theme instanceof MeteorGuiTheme mt) return mt.outlineColor.get();
        return new Color(0, 0, 0, 255);
    }

    // ---------------------------------------------------------------- rendering

    @Override
    protected void onRender(GuiRenderer r, double mouseX, double mouseY, double delta) {
        mx = mouseX;
        my = mouseY;

        ChessSession s = ChessSession.get();
        Pos pos = s.game().pos();
        boolean wb = s.whiteAtBottom();
        validateSelection(s);

        double cell = cell();
        double ox = originX(), oy = originY();
        double border = theme.scale(2);

        r.quad(ox - border, oy - border, 8 * cell + border * 2, 8 * cell + border * 2, outline());

        Move last = s.game().lastMove();
        int checkSq = pos.inCheck() ? pos.kingSq(pos.whiteToMove) : -1;
        boolean[] target = new boolean[64];
        if (selected >= 0 && promoFrom < 0) {
            for (Move m : s.movesFrom(selected)) target[m.to()] = true;
        }
        int hoverSq = promoFrom < 0 ? squareAt(mouseX, mouseY, wb) : -1;

        // squares and highlights
        for (int sq = 0; sq < 64; sq++) {
            int file = sq & 7, rank = sq >> 3;
            int col = wb ? file : 7 - file;
            int row = wb ? 7 - rank : rank;
            double cx = ox + col * cell, cy = oy + row * cell;

            r.quad(cx, cy, cell, cell, ((file + rank) & 1) == 0 ? DARK_SQUARE : LIGHT_SQUARE);
            if (last != null && (sq == last.from() || sq == last.to())) r.quad(cx, cy, cell, cell, accent(90));
            if (sq == checkSq) r.quad(cx, cy, cell, cell, CHECK);
            if (sq == selected) r.quad(cx, cy, cell, cell, accent(170));

            if (target[sq]) {
                boolean capture = pos.b[sq] != 0 || (Math.abs(pos.b[selected]) == Pos.P && sq == pos.ep);
                if (capture) {
                    r.quad(cx, cy, cell, cell, accent(125));
                } else {
                    double d = cell * 0.30;
                    r.quad(cx + (cell - d) / 2, cy + (cell - d) / 2, d, d, GuiRenderer.CIRCLE, accent(215));
                }
            }
            if (sq == hoverSq && s.isMyTurn() && (target[sq] || isMine(s, pos.b[sq]))) r.quad(cx, cy, cell, cell, HOVER);
        }

        // coordinates
        Color tc = theme.textSecondaryColor();
        double th = theme.textHeight();
        for (int col = 0; col < 8; col++) {
            int file = wb ? col : 7 - col;
            String label = String.valueOf((char) ('a' + file));
            r.text(label, ox + col * cell + cell / 2 - theme.textWidth(label) / 2, oy + 8 * cell + theme.scale(4), tc, false);
        }
        for (int row = 0; row < 8; row++) {
            int rank = wb ? 7 - row : row;
            String label = String.valueOf(rank + 1);
            r.text(label, x + (gutter() - theme.textWidth(label)) / 2 - theme.scale(1), oy + row * cell + cell / 2 - th / 2, tc, false);
        }

        // promotion picker background (quads are batched before the piece textures)
        if (promoFrom >= 0) {
            int col = promoCol(wb), row0 = promoRow0(wb), dir = promoDir(wb);
            int hover = promoIndexAt(mouseX, mouseY, wb);
            r.quad(ox + col * cell - border, oy + Math.min(row0, row0 + dir * 3) * cell - border,
                cell + border * 2, cell * 4 + border * 2, accent(255));
            for (int i = 0; i < 4; i++) {
                double cx = ox + col * cell, cy = oy + (row0 + dir * i) * cell;
                r.quad(cx, cy, cell, cell, PROMO_BG);
                if (i == hover) r.quad(cx, cy, cell, cell, accent(110));
            }
        }

        // pieces
        double ps = cell * 0.94;
        for (int sq = 0; sq < 64; sq++) {
            int piece = pos.b[sq];
            if (piece == 0) continue;
            int file = sq & 7, rank = sq >> 3;
            int col = wb ? file : 7 - file;
            int row = wb ? 7 - rank : rank;
            if (inPromoStrip(col, row, wb)) continue;
            drawPiece(r, piece, ox + col * cell + (cell - ps) / 2, oy + row * cell + (cell - ps) / 2, ps);
        }
        if (promoFrom >= 0) {
            int col = promoCol(wb), row0 = promoRow0(wb), dir = promoDir(wb);
            for (int i = 0; i < 4; i++) {
                int piece = promoWhite ? PROMO_ORDER[i] : -PROMO_ORDER[i];
                drawPiece(r, piece, ox + col * cell + (cell - ps) / 2, oy + (row0 + dir * i) * cell + (cell - ps) / 2, ps);
            }
        }
    }

    private void drawPiece(GuiRenderer r, int piece, double px, double py, double size) {
        Texture t = PieceTextures.get(piece);
        if (t != null) r.texture(px, py, size, size, 0, t);
    }

    private static boolean isMine(ChessSession s, int piece) {
        return piece != 0 && (piece > 0) == s.playerWhite();
    }

    private void validateSelection(ChessSession s) {
        boolean stale = selected >= 0 && (!s.isMyTurn() || !isMine(s, s.game().pos().b[selected])
            || selectedPly != s.game().plyCount());
        if (stale) selected = -1;
        if (promoFrom >= 0 && (!s.isMyTurn() || selectedPly != s.game().plyCount())) promoFrom = -1;
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean onMouseClicked(MouseButtonEvent click, boolean doubled) {
        if (!isOver(mx, my)) return false;

        ChessSession s = ChessSession.get();
        boolean wb = s.whiteAtBottom();

        if (click.button() == MOUSE_BUTTON_RIGHT) {
            selected = -1;
            promoFrom = -1;
            return true;
        }
        if (click.button() != MOUSE_BUTTON_LEFT) return false;

        if (promoFrom >= 0) {
            int idx = promoIndexAt(mx, my, wb);
            int from = promoFrom, to = promoTo;
            promoFrom = -1;
            selected = -1;
            if (idx >= 0) s.tryMove(new Move(from, to, PROMO_ORDER[idx]));
            return true;
        }

        int sq = squareAt(mx, my, wb);
        if (sq < 0 || !s.isMyTurn()) {
            selected = -1;
            return true;
        }

        Pos pos = s.game().pos();
        if (selected >= 0 && sq != selected) {
            List<Move> options = new ArrayList<>();
            for (Move m : s.movesFrom(selected)) if (m.to() == sq) options.add(m);
            if (!options.isEmpty()) {
                if (options.size() > 1) {
                    promoFrom = selected;
                    promoTo = sq;
                    promoWhite = pos.whiteToMove;
                } else {
                    s.tryMove(options.get(0));
                    selected = -1;
                }
                return true;
            }
        }

        if (isMine(s, pos.b[sq]) && sq != selected) {
            selected = sq;
            selectedPly = s.game().plyCount();
        } else {
            selected = -1;
        }
        return true;
    }
}
