package io.orchesty.sdk.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests the {@link ApplicationInstall} array form returned to the platform and date parsing. */
class ApplicationInstallTest {

    @Test
    void datesUse24HourClock() {
        Instant afternoon = Instant.parse("2026-10-19T15:04:05Z");
        ApplicationInstall install = new ApplicationInstall().setExpires(afternoon);

        Map<String, Object> out = install.toArray();

        assertEquals("2026-10-19 15:04:05", out.get("expires"));
    }

    @Test
    void unparseableExpiryIsUnknownNotNow() {
        ApplicationInstall install = ApplicationInstall.fromDocument(
                Map.of("created", "garbage", "expires", "not-a-date", "updated", "2026-10-19T15:04:05.000Z"));

        assertNull(install.getExpires());
        assertNotNull(install.getCreated());
        assertEquals(Instant.parse("2026-10-19T15:04:05Z"), install.getUpdated());
    }
}
