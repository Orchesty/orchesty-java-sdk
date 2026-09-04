package io.orchesty.sdk.transport;

import io.orchesty.sdk.process.ProcessDto;
import java.util.Map;

/**
 * Read-only view of an outbound HTTP request.
 */
public interface IRequestDto {

    String getUrl();

    HttpMethods getMethod();

    Object getBody();

    Map<String, String> getHeaders();

    int getTimeout();

    /**
     * Returns the current {@link ProcessDto}, used to correlate logs and metrics for this call.
     *
     * @return the correlation process DTO
     */
    ProcessDto getDebugInfo();
}
