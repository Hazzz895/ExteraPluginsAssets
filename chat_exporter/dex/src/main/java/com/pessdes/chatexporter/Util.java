package com.pessdes.chatexporter;

import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;
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
            if (message.legacy) {
                message.readAttachPath(stream, currentUserId);
            }
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
}
