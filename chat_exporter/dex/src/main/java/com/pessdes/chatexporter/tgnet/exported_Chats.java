package com.pessdes.chatexporter.tgnet;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.TLObject;

import java.util.ArrayList;

public abstract class exported_Chats extends TLObject {
    public ArrayList<exported_Chat> chats = new ArrayList<>();

    public static exported_Chats TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
        exported_Chats result = null;
        switch (constructor) {
            case TL_exported_Chats.constructor:
                result = new TL_exported_Chats();
                break;
        }
        if (result == null && exception) {
            throw new RuntimeException(String.format("can't parse magic %x in exported_Chats", constructor));
        }
        if (result != null) {
            result.readParams(stream, exception);
        }
        return result;
    }
}
