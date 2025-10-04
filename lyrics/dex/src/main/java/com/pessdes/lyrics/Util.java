package com.pessdes.lyrics;

import org.jetbrains.annotations.Nullable;

public class Util {
    public static String trimStringWithEllipsis(@Nullable String str, int maxLength) {
        if (str == null) {
            return "null";
        }
        if (str.length() > maxLength) {
            return str.substring(0, maxLength - 3) + "...";
        }
        return str;
    }

    public static int mixColors(int color1, int color2) {
        int r = (((color1 >> 16) & 0xFF) + ((color2 >> 16) & 0xFF)) / 2;
        int g = (((color1 >> 8) & 0xFF) + ((color2 >> 8) & 0xFF)) / 2;
        int b = ((color1 & 0xFF) + (color2 & 0xFF)) / 2;

        return (r << 16) | (g << 8) | b;
    }
}
