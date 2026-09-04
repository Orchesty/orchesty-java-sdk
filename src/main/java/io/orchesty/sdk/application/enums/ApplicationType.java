package io.orchesty.sdk.application.enums;

/** Application type identifiers (cron or webhook) with helpers to test them. */
public final class ApplicationType {

    public static final String CRON = "cron";
    public static final String WEBHOOK = "webhook";

    private ApplicationType() {}

    public static boolean isWebhook(String type) {
        return WEBHOOK.equals(type);
    }

    public static boolean isCron(String type) {
        return CRON.equals(type);
    }
}
