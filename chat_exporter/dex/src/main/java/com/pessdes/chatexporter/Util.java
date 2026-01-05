package com.pessdes.chatexporter;

import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
public class Util {
    public static ArrayList<TLRPC.Message> deserializeMessages(InputSerializedData stream, boolean exception) {
        int magic = stream.readInt32(exception);
        if (magic != Vector.constructor) { // Vector.constructor
            if (exception) {
                throw new RuntimeException(String.format("wrong Vector magic, got %x", magic));
            }
            return null;
        }
        int count = stream.readInt32(exception);
        var messages = new ArrayList<TLRPC.Message>(count);
        long currentUserId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        for (int a = 0; a < count; a++) {
            int constructor = stream.readInt32(exception);
            TLRPC.Message message = TLRPC.Message.TLdeserialize(stream, constructor, exception);
            if (message == null) {
                if (exception) {
                    throw new RuntimeException(String.format("can't parse magic %x in Message", constructor));
                }
                return null;
            }
            message.readAttachPath(stream, currentUserId);
            messages.add(message);
        }
        return messages;
    }

    public static void serializeMessages(OutputSerializedData stream, ArrayList<TLRPC.Message> messages) {
        stream.writeInt32(Vector.constructor);
        stream.writeInt32(messages.size());

        for (var message : messages) {
            boolean oLegacy = message.legacy;
            message.legacy = true;

            message.serializeToStream(stream);

            message.legacy = oLegacy;
        }
    }

    public static void setPrivateField(Object object, String fieldName, Object value, Class<?> clazz){
        try {
            if (clazz == null) clazz = object.getClass();
            var field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(object, value);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Method logMethod = null;

    static  {
        try {
            var appUtils = Class.forName("com.exteragram.messenger.utils.AppUtils");
            logMethod = appUtils.getDeclaredMethod("log", String.class);
        } catch (NoSuchMethodException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static void logInternal(String message) {
        try {
            logMethod.invoke(null, message);
        } catch (IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException(e);
        }
    }

    public static void log(String message) {
        logInternal(String.format("[%s:dex] %s", "chat_exporter", message));
    }

    public static void log(Object... objects) {
        StringBuilder builder = new StringBuilder();
        if (objects.length != 1) {
            builder.append("(");
        }
        for (int i = 0; i < objects.length; i++) {
            var object = objects[i];
            builder.append(object);
            if (i != objects.length - 1) {
                builder.append(", ");
            }
        }
        if (objects.length != 1) {
            builder.append(")");
        }
        log(builder.toString());
    }
}
