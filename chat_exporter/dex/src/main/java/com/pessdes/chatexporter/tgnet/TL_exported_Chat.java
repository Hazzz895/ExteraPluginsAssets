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
        messages = Vector.deserialize(stream, TLRPC.Message::TLdeserialize, exception);
        readPeer(stream, exception);
    }

    @Override
    public void serializeToStream(OutputSerializedData stream) {
        stream.writeInt32(constructor);
        Vector.serialize(stream, messages);
        peer.serializeToStream(stream);
    }

}