package io.orchesty.sdk.process;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Message context for a <b>batch</b> node.
 * Unlike a connector (one message in, one message out), a batch emits <b>many</b>
 * messages: the Bridge splits them and fans each one out to the follower(s).
 *
 * <p>The node reads its trigger payload via the inherited {@link #getJsonData()} (and
 * {@link #getBatchCursor()} when paging) and produces output by calling
 * {@link #addItem(Object)} / {@link #setItemList(List, boolean)}. On the wire the
 * {@link #getBridgeData() body} is a JSON <b>array</b> of {@code { body, headers }}
 * messages (each {@code body} a string) — exactly what the Bridge's batch worker
 * expects ({@code bridge/pkg/worker/batch.go}).
 *
 * <p>Paging is optional: {@link #setBatchCursor(String, boolean)} sets the {@code cursor}
 * header and the batch result-code so the Bridge re-invokes this node with the cursor
 * (with or without also fanning out the current messages).
 */
public class BatchProcessDto extends ProcessDto {

    /** A single emitted message: its own body (raw JSON string) and headers. */
    public static final class Message {

        private final String body;
        private final Map<String, Object> headers;

        public Message(String body, Map<String, Object> headers) {
            this.body = body;
            this.headers = headers == null ? new LinkedHashMap<>() : headers;
        }

        public String getBody() {
            return body;
        }

        public Map<String, Object> getHeaders() {
            return headers;
        }
    }

    private final List<Message> messages = new ArrayList<>();

    public List<Message> getMessages() {
        return messages;
    }

    public BatchProcessDto setMessages(List<Message> newMessages) {
        this.messages.clear();
        if (newMessages != null) {
            this.messages.addAll(newMessages);
        }
        return this;
    }

    // ------------------------------------------------------------- emit items

    /**
     * Emits one message; non-String bodies are serialized to JSON.
     *
     * @param body message body
     * @return this DTO
     */
    public BatchProcessDto addItem(Object body) {
        return addItem(body, null, null, null);
    }

    /**
     * Emits one message with optional per-item {@code user}, a {@code limiter-key} and
     * extra headers (e.g. a per-item {@code audit-entity}).
     *
     * @param body message body; non-String bodies are serialized to JSON
     * @param user per-item user, or {@code null}
     * @param limit {@code limiter-key} header value, or {@code null}
     * @param headers extra message headers, or {@code null}
     * @return this DTO
     */
    public BatchProcessDto addItem(Object body, String user, String limit, Map<String, Object> headers) {
        String b = body instanceof String s ? s : Json.toString(body);
        Map<String, Object> h = new LinkedHashMap<>();
        if (limit != null && !limit.isEmpty()) {
            h.put(Headers.LIMITER_KEY, mergeLimiterKey(getHeader(Headers.LIMITER_KEY), limit));
        }
        if (user != null && !user.isEmpty()) {
            h.put(Headers.USER, user);
        }
        if (headers != null) {
            h.putAll(headers);
        }
        messages.add(new Message(b, h));
        return this;
    }

    /**
     * Merges an item's limiter key into the inherited {@code limiter-key} header.
     * Both use groups of three parts ({@code key;time;amount}); the item's group replaces an
     * inherited group with the same key, other inherited groups are kept.
     *
     * @param inherited the DTO's {@code limiter-key} header, or {@code null}
     * @param limit the item's limiter key
     * @return the merged limiter key
     */
    static String mergeLimiterKey(String inherited, String limit) {
        Map<String, String> limits = new LinkedHashMap<>();
        if (inherited != null && !inherited.isEmpty()) {
            String[] parts = inherited.split(";", -1);
            for (int i = 0; i + 2 < parts.length; i += 3) {
                limits.put(parts[i], parts[i] + ";" + parts[i + 1] + ";" + parts[i + 2]);
            }
        }
        limits.put(limit.split(";", -1)[0], limit);
        return String.join(";", limits.values());
    }

    public BatchProcessDto addMessage(Message message) {
        if (message != null) {
            messages.add(message);
        }
        return this;
    }

    /**
     * Emits a whole list.
     * With {@code asBatch=false} (fan-out) each element becomes its own message; with
     * {@code asBatch=true} the whole list is emitted as a single message whose body is
     * the array.
     *
     * @param list items to emit; {@code null} is ignored
     * @param asBatch whether to emit the list as a single message
     * @return this DTO
     */
    public BatchProcessDto setItemList(List<?> list, boolean asBatch) {
        if (list == null) {
            return this;
        }
        if (asBatch) {
            addItem(List.of(list));
        } else {
            for (Object it : list) {
                addItem(it);
            }
        }
        return this;
    }

    // ----------------------------------------------------------------- cursor

    /**
     * Asks the Bridge to re-invoke this batch with {@code cursor}.
     * When {@code iterateOnly} is {@code true} the current messages are NOT fanned out
     * (pure iterator, code {@link ResultCode#BATCH_CURSOR_ONLY}); otherwise they are
     * fanned out AND the cursor is followed ({@link ResultCode#BATCH_CURSOR_WITH_FOLLOWERS}).
     *
     * @param cursor cursor passed to the next invocation
     * @param iterateOnly whether to skip fanning out the current messages
     * @return this DTO
     */
    public BatchProcessDto setBatchCursor(String cursor, boolean iterateOnly) {
        addHeader(Headers.BATCH_CURSOR, cursor);
        if (iterateOnly) {
            setStatusHeader(
                    ResultCode.BATCH_CURSOR_ONLY,
                    "Message will be used as an iterator with cursor [" + cursor + "]. No follower will be called.");
        } else {
            setStatusHeader(
                    ResultCode.BATCH_CURSOR_WITH_FOLLOWERS,
                    "Message will be used as an iterator with cursor [" + cursor
                            + "]. Data will be sent to follower(s).");
        }
        return this;
    }

    public String getBatchCursor() {
        return getBatchCursor("");
    }

    public String getBatchCursor(String defaultValue) {
        return getHeader(Headers.BATCH_CURSOR, defaultValue);
    }

    public BatchProcessDto removeBatchCursor() {
        removeHeader(Headers.BATCH_CURSOR);
        removeRelatedHeaders(Set.of(ResultCode.BATCH_CURSOR_ONLY, ResultCode.BATCH_CURSOR_WITH_FOLLOWERS));
        return this;
    }

    // --------------------------------------------------------------- envelope

    /**
     * Returns the batch envelope body: a JSON array of {@code { body, headers }} messages.
     * The Bridge {@code json.Unmarshal}s this into {@code []MessageDto} and fans out.
     *
     * @return the serialized message array
     */
    @Override
    public Object getBridgeData() {
        return Json.toString(messages);
    }
}
