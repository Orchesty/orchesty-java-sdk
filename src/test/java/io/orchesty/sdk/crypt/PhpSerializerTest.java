package io.orchesty.sdk.crypt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Tests {@link PhpSerializer} edge cases against PHP's {@code serialize()} format. */
class PhpSerializerTest {

    @Test
    void wholeDoublesBeyondLongRangeKeepTheirValue() {
        assertEquals("d:3;", PhpSerializer.serialize(3.0));
        assertEquals("d:1.5;", PhpSerializer.serialize(1.5));
        assertEquals("d:100000000000000000000;", PhpSerializer.serialize(1e20));
        assertEquals(1e20, PhpSerializer.unserialize(PhpSerializer.serialize(1e20)));
    }

    @Test
    void nanAndInfinityAreRejectedOnSerialize() {
        assertThrows(IllegalArgumentException.class, () -> PhpSerializer.serialize(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> PhpSerializer.serialize(Double.POSITIVE_INFINITY));
        assertThrows(
                IllegalArgumentException.class, () -> PhpSerializer.serialize(Map.of("x", Double.NEGATIVE_INFINITY)));
    }

    @Test
    void phpInfAndNanAreRead() {
        assertEquals(Double.POSITIVE_INFINITY, PhpSerializer.unserialize("d:INF;"));
        assertEquals(Double.NEGATIVE_INFINITY, PhpSerializer.unserialize("d:-INF;"));
        assertTrue(Double.isNaN((Double) PhpSerializer.unserialize("d:NAN;")));
    }

    @Test
    void emptyArrayReadsAsMapAndListsStayLists() {
        assertEquals(Map.of(), PhpSerializer.unserialize("a:0:{}"));
        assertEquals(List.of("a", "b"), PhpSerializer.unserialize(PhpSerializer.serialize(List.of("a", "b"))));
    }
}
