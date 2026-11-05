package me.playerp6.dailyRewards;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public final class Text {

    // Понимает &a и HEX-цвета &#FF8800
    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.builder()
            .character('&')
            .hexColors()
            .build();

    private Text() {
    }

    public static Component color(String text) {
        if (text == null) text = "";
        text = text.replace('§', '&');
        return SERIALIZER.deserialize(text).decoration(TextDecoration.ITALIC, false);
    }
}