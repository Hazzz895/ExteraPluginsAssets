package com.pessdes.chatexporter.tgnet;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.InputSerializedData;

import java.util.ArrayList;

public abstract class exported_Chat extends TLObject {
    public ArrayList<TLRPC.Message> messages = new ArrayList<>();
    public TLObject peer;

    public static exported_Chat TLdeserialize(InputSerializedData stream, int constructor, boolean exception) {
        exported_Chat result = null;
        switch (constructor) {
            case TL_exported_Chat.constructor:
                result = new TL_exported_Chat();
                break;
        }
        if (result == null && exception) {
            throw new RuntimeException(String.format("can't parse magic %x in exported_Chat", constructor));
        }
        if (result != null) {
            result.readParams(stream, exception);
        }
        return result;
    }

    protected void readPeer(InputSerializedData stream, boolean exception) {
        var constructor = stream.readInt32(exception);
        peer = TLRPC.Chat.TLdeserialize(stream, constructor, false);
        if (peer == null) {
            peer = TLRPC.User.TLdeserialize(stream, constructor, false);
        }
        if (peer == null && exception) {
            throw new RuntimeException(String.format("can't parse magic %x in exported_Chat (argument peer)", constructor));
        }
    }
}

