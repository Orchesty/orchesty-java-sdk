package io.orchesty.sdk.application.enums;

/** Keys of the built-in application forms (authorization and limiter) and their display names. */
public final class CoreForm {

    public static final String AUTHORIZATION_FORM = "authorization_form";
    public static final String LIMITER_FORM = "limiter_form";

    private CoreForm() {}

    public static String getFormName(String key) {
        switch (key) {
            case AUTHORIZATION_FORM:
                return "Authorization";
            case LIMITER_FORM:
                return "Limiter";
            default:
                return "Unknown";
        }
    }
}
