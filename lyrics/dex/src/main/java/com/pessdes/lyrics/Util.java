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
}
