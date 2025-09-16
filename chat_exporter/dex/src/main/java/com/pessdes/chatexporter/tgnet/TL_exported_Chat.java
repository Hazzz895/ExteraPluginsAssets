package com.pessdes.chatexporter.tgnet;

import com.pessdes.chatexporter.Util;

import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.Vector;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public class TL_exported_Chat extends exported_Chat {
    public static final int constructor = 0x67670001;

    @Override
    public void readParams(InputSerializedData stream, boolean exception) {
        int vectorConstructor = stream.readInt32(exception);
        if (vectorConstructor != 0x1cb5c415) {
            if (exception) {
                throw new RuntimeException(String.format("wrong Vector magic, got %x", vectorConstructor));
            }
            return;
        }
        int count = stream.readInt32(exception);
        messages = new ArrayList<TLRPC.Message>(count);
        for (int a = 0; a < count; a++) {
            TLRPC.Message message = TLRPC.Message.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (message == null) {
                return;
            }
            message.attachPath = stream.readString(exception);
            messages.add(message);
        }
        readPeer(stream, exception);
    }

    @Override
    public void serializeToStream(OutputSerializedData stream) {
        stream.writeInt32(constructor);
        Vector.serialize(stream, messages);
        peer.serializeToStream(stream);
    }

}