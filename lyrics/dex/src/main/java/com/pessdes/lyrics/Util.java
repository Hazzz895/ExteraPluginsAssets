package com.pessdes.lyrics;

import android.graphics.Color;

import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

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

    /**
     * @param alpha Значение 0т 0 до 1
     */
    public static int applyAlpha(int color, double alpha, boolean override) {
        int baseAlpha = Color.alpha(color);
        int newAlpha = override
                ? (int)(255 * alpha)
                : (int)(baseAlpha * alpha);

        newAlpha = Math.max(0, Math.min(255, newAlpha));

        return Color.argb(newAlpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    public static int applyAlpha(int color, double alpha) {
        return applyAlpha(color, alpha, false);
    }

    public static Object getPrivateField(Object target, String fieldName) {
        try {
            Class<?> clazz = target.getClass();
            Field field = null;

            while (clazz != null) {
                try {
                    field = clazz.getDeclaredField(fieldName);
                    break;
                } catch (NoSuchFieldException e) {
                    clazz = clazz.getSuperclass();
                }
            }

            if (field == null) {
                throw new NoSuchFieldException("Field '" + fieldName + "' not found");
            }

            field.setAccessible(true);
            return field.get(target);
        } catch (Exception e) {
            throw new RuntimeException("Failed to get field '" + fieldName + "'", e);
        }
    }
}
