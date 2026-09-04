package io.orchesty.sdk.transport;

/**
 * Read-only view of an HTTP response.
 */
public interface IResponseDto {

    String getBody();

    int getResponseCode();

    String getReason();
}
