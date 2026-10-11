package com.rumble5930.chess.gui;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.FilterMode;
import meteordevelopment.meteorclient.renderer.Texture;
import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/** Lazily loads the piece sprites from the addon jar. Must be called on the render thread. */
public final class PieceTextures {
    private static final Texture[] CACHE = new Texture[12];
    private static final boolean[] FAILED = new boolean[12];

    private PieceTextures() {}

    /** @param piece positive = white, negative = black, 1..6 = pawn..king (see Pos). May return null if missing. */
    public static Texture get(int piece) {
        int type = Math.abs(piece);
        int i = (piece > 0 ? 0 : 6) + type - 1;
        if (CACHE[i] == null && !FAILED[i]) {
            CACHE[i] = load((piece > 0 ? "w" : "b") + "pnbrqk".charAt(type - 1));
            if (CACHE[i] == null) FAILED[i] = true;
        }
        return CACHE[i];
    }

    private static Texture load(String name) {
        String path = "/assets/chess/pieces/" + name + ".png";
        try (InputStream in = PieceTextures.class.getResourceAsStream(path)) {
            if (in == null) return null;

            byte[] bytes = in.readAllBytes();
            ByteBuffer data = BufferUtils.createByteBuffer(bytes.length);
            data.put(bytes);
            data.flip();

            try (MemoryStack stack = MemoryStack.stackPush()) {
                IntBuffer width = stack.mallocInt(1);
                IntBuffer height = stack.mallocInt(1);
                IntBuffer comp = stack.mallocInt(1);

                ByteBuffer image = STBImage.stbi_load_from_memory(data, width, height, comp, 4);
                if (image == null) return null;

                Texture texture = new Texture(width.get(0), height.get(0), GpuFormat.RGBA8_UNORM, FilterMode.LINEAR, FilterMode.LINEAR);
                texture.upload(image);
                STBImage.stbi_image_free(image);
                return texture;
            }
        } catch (IOException e) {
            return null;
        }
    }
}
