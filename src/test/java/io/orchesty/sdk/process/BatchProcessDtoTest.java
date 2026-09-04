package io.orchesty.sdk.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BatchProcessDtoTest {

    private static List<Map<String, Object>> asMessages(BatchProcessDto dto) {
        return Json.fromString((String) dto.getBridgeData(), new TypeReference<>() {});
    }

    @Test
    void addItemSerializesBodiesAndBuildsMessageArray() {
        BatchProcessDto dto = new BatchProcessDto();
        dto.addItem(Map.of("id", 1));
        dto.addItem("raw-string");

        List<Map<String, Object>> messages = asMessages(dto);
        assertEquals(2, messages.size());
        // non-String body is serialized to JSON text
        assertEquals("{\"id\":1}", messages.get(0).get("body"));
        // String body is passed through verbatim
        assertEquals("raw-string", messages.get(1).get("body"));
        // every message carries a headers object
        assertTrue(messages.get(0).containsKey("headers"));
    }

    @Test
    void itemLimitIsMergedIntoInheritedLimiterKey() {
        BatchProcessDto dto = new BatchProcessDto();
        dto.addHeader(Headers.LIMITER_KEY, "app|user;60;100;group;1;10");

        dto.addItem("a", null, "item;1;5", null);
        dto.addItem("b", null, "app|user;30;50", null);

        List<Map<String, Object>> messages = asMessages(dto);
        assertEquals("app|user;60;100;group;1;10;item;1;5", limiterKey(messages.get(0)));
        assertEquals("app|user;30;50;group;1;10", limiterKey(messages.get(1)));
    }

    @Test
    void mergeLimiterKeyMatchesNodeParseLimitKey() {
        assertEquals("k;1;2", BatchProcessDto.mergeLimiterKey(null, "k;1;2"));
        assertEquals("k;1;2", BatchProcessDto.mergeLimiterKey("", "k;1;2"));
        // An item limit with a group keeps all six parts under its first key.
        assertEquals("a;1;1;k;1;2;g;3;4", BatchProcessDto.mergeLimiterKey("a;1;1", "k;1;2;g;3;4"));
    }

    @SuppressWarnings("unchecked")
    private static Object limiterKey(Map<String, Object> message) {
        return ((Map<String, Object>) message.get("headers")).get(Headers.LIMITER_KEY);
    }

    @Test
    void addItemCarriesPerItemUserAndHeaders() {
        BatchProcessDto dto = new BatchProcessDto();
        dto.addItem(Map.of("id", 1), "user-42", null, Map.of(Headers.AUDIT_ENTITY, "{\"order\":{}}"));

        Map<String, Object> msg = asMessages(dto).get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> headers = (Map<String, Object>) msg.get("headers");
        assertEquals("user-42", headers.get(Headers.USER));
        assertEquals("{\"order\":{}}", headers.get(Headers.AUDIT_ENTITY));
    }

    @Test
    void setItemListFanOutVersusAsBatch() {
        BatchProcessDto fan = new BatchProcessDto();
        fan.setItemList(List.of("a", "b", "c"), false);
        assertEquals(3, asMessages(fan).size());

        BatchProcessDto single = new BatchProcessDto();
        single.setItemList(List.of("a", "b", "c"), true);
        assertEquals(1, asMessages(single).size());
    }

    @Test
    void setBatchCursorWithFollowersSetsHeaderAndResultCode() {
        BatchProcessDto dto = new BatchProcessDto();
        dto.addItem(Map.of("id", 1));
        dto.setBatchCursor("page-2", false);

        assertEquals("page-2", dto.getBatchCursor());
        assertEquals(
                Integer.toString(ResultCode.BATCH_CURSOR_WITH_FOLLOWERS.code()), dto.getHeader(Headers.RESULT_CODE));
    }

    @Test
    void setBatchCursorIterateOnlyUsesCursorOnlyCode() {
        BatchProcessDto dto = new BatchProcessDto();
        dto.setBatchCursor("page-2", true);

        assertEquals(Integer.toString(ResultCode.BATCH_CURSOR_ONLY.code()), dto.getHeader(Headers.RESULT_CODE));
    }

    @Test
    void removeBatchCursorClearsHeaderAndRelatedResultCode() {
        BatchProcessDto dto = new BatchProcessDto();
        dto.setBatchCursor("page-2", true);
        dto.removeBatchCursor();

        assertEquals("", dto.getBatchCursor());
        assertFalse(dto.hasHeader(Headers.RESULT_CODE));
    }

    @Test
    void triggerPayloadIsReadableAsInput() {
        BatchProcessDto dto = new BatchProcessDto();
        dto.setData("{\"since\":\"2026-01-01\"}");
        assertEquals("2026-01-01", dto.getJsonData().get("since"));
        // no items emitted yet -> empty array body
        assertEquals("[]", dto.getBridgeData());
    }
}
