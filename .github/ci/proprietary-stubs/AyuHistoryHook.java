package com.radolyn.ayugram.proprietary;

import android.util.Pair;
import android.util.SparseArray;

import org.telegram.messenger.MessageObject;

import java.util.List;

/**
 * CI-only stand-in for AyuGram's private "proprietary" submodule, which a fork can't check out.
 * Used only by .github/workflows/test-apk.yml. Deleted-message history in chats is disabled in these builds.
 */
public class AyuHistoryHook {
    public static Pair<Integer, Integer> getMinAndMaxIds(List<MessageObject> messages) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        if (messages != null) {
            for (MessageObject message : messages) {
                int id = message.getId();
                if (id < min) min = id;
                if (id > max) max = id;
            }
        }
        if (min == Integer.MAX_VALUE) {
            min = 0;
            max = 0;
        }
        return new Pair<>(min, max);
    }

    public static void doHook(int currentAccount, List<MessageObject> messages, SparseArray<MessageObject>[] messagesDict,
                              int startId, int endId, long dialogId, int limit, long topicId, boolean isSecretChat) {
        // no-op in CI test builds
    }
}
