package com.pessdes.chatexporter.tgnet;

import android.text.TextUtils;

import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.InputSerializedData;
import org.telegram.tgnet.OutputSerializedData;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;

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
        messages = new ArrayList<>(count);
        long currentUserId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        for (int a = 0; a < count; a++) {
            TLRPC.Message message = TLRPC.Message.TLdeserialize(stream, stream.readInt32(exception), exception);
            if (message == null) {
                return;
            }
            message.readAttachPath(stream, currentUserId);
            messages.add(message);
        }
        readPeer(stream, exception);
    }

    @Override
    public void serializeToStream(OutputSerializedData stream) {
        stream.writeInt32(constructor);
        stream.writeInt32(0x1cb5c415);
        stream.writeInt32(messages.size());
        long currentUserId = UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
        for (TLRPC.Message message : messages) {
            message.serializeToStream(stream);

            // Symmetrical write logic, reverse-engineered from readAttachPath
            boolean hasMedia = message.media != null && !(message.media instanceof TLRPC.TL_messageMediaEmpty) && !(message.media instanceof TLRPC.TL_messageMediaWebPage);
            if ((message.out || message.peer_id != null && message.from_id != null && message.peer_id.user_id != 0 && message.peer_id.user_id == message.from_id.user_id && message.from_id.user_id == currentUserId) && (message.id < 0 || hasMedia || message.send_state == 3) || message.legacy) {
                String path = message.attachPath != null ? message.attachPath : "";
                if ((message.id < 0 || message.send_state == 3 || message.legacy) && message.params != null && !message.params.isEmpty()) {
                    StringBuilder builder = new StringBuilder();
                    for (java.util.Map.Entry<String, String> entry : message.params.entrySet()) {
                        builder.append(entry.getKey()).append("|=|").append(entry.getValue()).append("||");
                    }
                    path = "||" + builder.toString() + path;
                }
                stream.writeString(path);
            }
            if ((message.flags & TLRPC.MESSAGE_FLAG_FWD) != 0 && message.id < 0) {
                stream.writeInt32(message.fwd_msg_id);
            }
        }
        peer.serializeToStream(stream);
    }

}