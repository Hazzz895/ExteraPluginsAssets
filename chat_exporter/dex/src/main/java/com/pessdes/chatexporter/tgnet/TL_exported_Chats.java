package com.pessdes.chatexporter.tgnet;

import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.Vector;

public class TL_exported_Chats extends exported_Chats {
    public static final int constructor = 0x67670101;

    @Override
    public void readParams(InputSerializedData stream, boolean exception) {
        chats = Vector.deserialize(stream, exported_Chat::TLdeserialize, exception);
    }
    @Override
    public void serializeToStream(OutputSerializedData stream) {
        stream.writeInt32(constructor);
        Vector.serialize(stream, chats);
    }
}
