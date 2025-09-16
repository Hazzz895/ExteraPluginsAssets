package com.pessdes.chatexporter;

import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.Vector;

import java.lang.reflect.Method;
import java.util.ArrayList;
public class Util {
    public static ArrayList<TLRPC.Message> deserializeMessages(InputSerializedData stream, boolean exception) {
        var constructor = stream.readInt32(exception);
        if (constructor != Vector.constructor) {
            if (exception) {
                throw new RuntimeException(String.format("can't parse magic %x in Vector", constructor));
            }
            return new ArrayList<>();
        }

        var size = stream.readInt32(exception);
        var result = new ArrayList<TLRPC.Message>();
        var userId = UserConfig.getInstance(UserConfig.selectedAccount).clientUserId;

        for (var i = 0; i < size; i++) {
            constructor = stream.readInt32(exception);
            var message = TLRPC.Message.TLdeserialize(stream, constructor, exception);
            if (message != null) {
                boolean oLegacy = message.legacy;
                message.legacy = true;

                message.readAttachPath(stream, userId);

                message.legacy = oLegacy;
                result.add(message);
            }
        }
        return result;
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
