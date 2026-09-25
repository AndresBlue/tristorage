package com.andresblue.tristorage.client;

import com.andresblue.tristorage.TriStorageMod;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.Text;

/** Resource-pack-friendly font hook shared by every TriStorage screen. */
public final class GuiText {
    private static final StyleSpriteSource.Font FONT =
            new StyleSpriteSource.Font(TriStorageMod.id("gui"));

    private GuiText() {
    }

    public static Text of(Text text) {
        return text.copy().styled(style -> style.withFont(FONT));
    }
}
