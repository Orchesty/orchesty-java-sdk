package io.orchesty.sdk.webhook;

import io.orchesty.sdk.application.ApplicationInstall;
import io.orchesty.sdk.application.IApplication;
import io.orchesty.sdk.transport.RequestDto;
import io.orchesty.sdk.transport.ResponseDto;
import java.util.List;

/**
 * Contract for a webhook-aware application.
 * Prefer extending {@link AWebhookApplication}, which wires up the sync UI actions and manager injection.
 */
public interface IWebhookApplication extends IApplication {

    List<WebhookSubscription> getWebhookSubscriptions();

    RequestDto getWebhookSubscribeRequestDto(
            ApplicationInstall applicationInstall, WebhookSubscription subscription, String url);

    RequestDto getWebhookUnsubscribeRequestDto(ApplicationInstall applicationInstall, Webhook webhook);

    String processWebhookSubscribeResponse(ResponseDto dto, ApplicationInstall applicationInstall);

    boolean processWebhookUnsubscribeResponse(ResponseDto dto);
}
