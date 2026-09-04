package io.orchesty.sdk.process;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProcessDtoTest {

    @Test
    void setLimiterDecoratesKey() {
        ProcessDto dto = new ProcessDto();
        dto.setLimiter("mailchimp", 60, 100);
        assertEquals("mailchimp|;60;100", dto.getHeader(Headers.LIMITER_KEY));
    }

    @Test
    void setLimiterWithGroupBuildsCompositeKey() {
        ProcessDto dto = new ProcessDto();
        dto.setLimiterWithGroup("app|user", 60, 100, "group", 3600, 1000);
        assertEquals("app|user;60;100;group|;3600;1000", dto.getHeader(Headers.LIMITER_KEY));
    }

    @Test
    void removeLimiterClearsHeader() {
        ProcessDto dto = new ProcessDto();
        dto.setLimiter("k", 1, 2).removeLimiter();
        assertNull(dto.getHeader(Headers.LIMITER_KEY));
    }

    @Test
    void setForceFollowersResolvesNamesToQueueIds() {
        ProcessDto dto = new ProcessDto();
        dto.addHeader(Headers.WORKER_FOLLOWERS, "[{\"name\":\"a\",\"id\":\"q-a\"},{\"name\":\"b\",\"id\":\"q-b\"}]");
        dto.setForceFollowers("b");

        assertEquals("q-b", dto.getHeader(Headers.FORCE_TARGET_QUEUE));
        assertEquals(Integer.toString(ResultCode.FORWARD_TO_TARGET_QUEUE.code()), dto.getHeader(Headers.RESULT_CODE));
    }

    @Test
    void setForceFollowersThrowsWhenUnreachable() {
        ProcessDto dto = new ProcessDto();
        dto.addHeader(Headers.WORKER_FOLLOWERS, "[{\"name\":\"a\",\"id\":\"q-a\"}]");
        assertThrows(IllegalArgumentException.class, () -> dto.setForceFollowers("missing"));
    }

    @Test
    void removeForceFollowersClearsHeaderAndRelatedCode() {
        ProcessDto dto = new ProcessDto();
        dto.addHeader(Headers.WORKER_FOLLOWERS, "[{\"name\":\"a\",\"id\":\"q-a\"}]");
        dto.setForceFollowers("a").removeForceFollowers();

        assertNull(dto.getHeader(Headers.FORCE_TARGET_QUEUE));
        assertNull(dto.getHeader(Headers.RESULT_CODE));
    }

    @Test
    void addAuditHeaderAccumulatesFieldsPerEntity() {
        ProcessDto dto = new ProcessDto();
        dto.addAuditHeader("Order", "id", List.of(Map.of("id", "1")));
        dto.addAuditHeader("Order", "id", List.of(Map.of("id", "2")));

        String header = dto.getHeader(Headers.AUDIT_ENTITY);
        Map<String, Object> audit = Json.fromString(header, new com.fasterxml.jackson.core.type.TypeReference<>() {});
        @SuppressWarnings("unchecked")
        Map<String, Object> order = (Map<String, Object>) audit.get("Order");
        assertEquals("id", order.get("key"));
        assertEquals(2, ((List<?>) order.get("fields")).size());
    }

    @Test
    void setFreeResetsState() {
        ProcessDto dto = new ProcessDto();
        dto.setData("{\"a\":1}").addHeader("x", "y").setCurrentApp("app");
        assertTrue(dto.isFree());

        dto.setFree(false);
        assertFalse(dto.isFree());

        dto.setFree(true);
        assertTrue(dto.isFree());
        assertEquals("", dto.getData());
        assertTrue(dto.getHeaders().isEmpty());
        assertEquals("", dto.getCurrentApp());
    }
}
