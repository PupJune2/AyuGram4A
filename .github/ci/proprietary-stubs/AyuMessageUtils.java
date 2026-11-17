package com.radolyn.ayugram.proprietary;

import com.radolyn.ayugram.database.entities.AyuMessageBase;
import com.radolyn.ayugram.messages.AyuSavePreferences;

import org.telegram.messenger.MessageObject;
import org.telegram.tgnet.TLRPC;

/**
 * CI-only stand-in for AyuGram's private "proprietary" submodule, which a fork can't check out.
 * Used only by .github/workflows/test-apk.yml. Saved edits and deletions keep text only (no media) in these builds.
 */
public class AyuMessageUtils {
    public static void map(AyuSavePreferences prefs, AyuMessageBase target) {
        if (prefs == null || target == null) return;
        TLRPC.Message msg = prefs.getMessage();
        target.userId = prefs.getUserId();
        target.dialogId = prefs.getDialogId();
        target.topicId = prefs.getTopicId();
        target.messageId = prefs.getMessageId();
        if (msg == null) return;
        target.date = msg.date;
        target.editDate = msg.edit_date;
        target.flags = msg.flags;
        target.groupedId = msg.grouped_id;
        target.peerId = MessageObject.getPeerId(msg.peer_id);
        target.fromId = MessageObject.getPeerId(msg.from_id);
        target.text = msg.message;
    }

    public static void mapMedia(AyuSavePreferences prefs, AyuMessageBase target, boolean copyMedia) {
    }

    public static void map(AyuMessageBase source, TLRPC.Message target, int currentAccount) {
        if (source == null || target == null) return;
        target.id = source.messageId;
        target.dialog_id = source.dialogId;
        target.date = source.date;
        target.edit_date = source.editDate;
        target.message = source.text != null ? source.text : "";
        target.peer_id = peerFor(source.peerId != 0 ? source.peerId : source.dialogId);
        if (source.fromId != 0) {
            target.from_id = peerFor(source.fromId);
        }
    }

    public static void mapMedia(AyuMessageBase source, TLRPC.Message target) {
        if (target != null && target.media == null) {
            target.media = new TLRPC.TL_messageMediaEmpty();
        }
    }

    private static TLRPC.Peer peerFor(long id) {
        if (id < 0) {
            TLRPC.TL_peerChannel peer = new TLRPC.TL_peerChannel();
            peer.channel_id = -id;
            return peer;
        }
        TLRPC.TL_peerUser peer = new TLRPC.TL_peerUser();
        peer.user_id = id;
        return peer;
    }
}
