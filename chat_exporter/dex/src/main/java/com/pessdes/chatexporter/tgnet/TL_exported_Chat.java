package com.pessdes.chatexporter.tgnet;

import com.pessdes.chatexporter.Util;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.Vector;
import org.telegram.tgnet.TLRPC;

public class TL_exported_Chat extends exported_Chat {
    public static final int constructor = 0x67670001;

    @Override
    public void readParams(InputSerializedData stream, boolean exception) {
        messages = Util.deserializeMessages(stream, exception);
        chat = TLRPC.Chat.TLdeserialize(stream, stream.readInt32(exception), exception);
    }

    @Override
    public void serializeToStream(OutputSerializedData stream) {
        stream.writeInt32(constructor);
        Vector.serialize(stream, messages);
        chat.serializeToStream(stream);
    }

}