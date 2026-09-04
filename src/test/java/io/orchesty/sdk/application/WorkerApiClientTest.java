package io.orchesty.sdk.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.ConnectException;
import java.net.ServerSocket;
import java.nio.channels.UnresolvedAddressException;
import org.junit.jupiter.api.Test;

/** Transport failures must name the request and the root cause instead of "failed: null". */
class WorkerApiClientTest {

    @Test
    void describeWalksNullMessageCauses() {
        Exception e = (Exception) new ConnectException().initCause(new UnresolvedAddressException());

        assertEquals("ConnectException <- UnresolvedAddressException", WorkerApiClient.describe(e));
    }

    @Test
    void connectionFailureNamesRequestAndCause() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        WorkerApiClient client = new WorkerApiClient("http://127.0.0.1:" + closedPort, "");

        IllegalStateException e =
                assertThrows(IllegalStateException.class, () -> client.send("/document/Node", "GET", null));

        assertTrue(
                e.getMessage().startsWith("Worker-API request GET /document/Node failed: ConnectException"),
                e.getMessage());
    }
}
