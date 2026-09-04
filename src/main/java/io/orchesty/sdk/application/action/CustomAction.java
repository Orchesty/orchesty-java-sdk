package io.orchesty.sdk.application.action;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A custom action shown on the application detail in the Orchesty UI.
 * It is either an {@code OPEN} link ({@code url}) or a {@code CALL} to a topology node
 * ({@code topologyName} + {@code nodeName}).
 */
public class CustomAction {

    private final String name;
    private final CustomActionType action;
    private String url;
    private String body;
    private String topologyName;
    private String nodeName;

    public CustomAction(
            String name, CustomActionType action, String url, String topologyName, String nodeName, String body) {
        if (url == null && (topologyName == null || nodeName == null)) {
            throw new IllegalArgumentException("One or more parameters are missing: url or topologyName and nodeName");
        }
        this.name = name;
        this.action = action;
        this.url = url;
        this.topologyName = topologyName;
        this.nodeName = nodeName;
        this.body = body;
    }

    public static CustomAction open(String name, String url) {
        return new CustomAction(name, CustomActionType.OPEN, url, null, null, null);
    }

    public static CustomAction call(String name, String topologyName, String nodeName, String body) {
        return new CustomAction(name, CustomActionType.CALL, null, topologyName, nodeName, body);
    }

    public String getName() {
        return name;
    }

    public CustomActionType getAction() {
        return action;
    }

    public String getUrl() {
        return url;
    }

    public CustomAction setUrl(String url) {
        this.url = url;
        return this;
    }

    public String getBody() {
        return body;
    }

    public CustomAction setBody(String body) {
        this.body = body;
        return this;
    }

    public String getTopologyName() {
        return topologyName;
    }

    public CustomAction setTopologyName(String topologyName) {
        this.topologyName = topologyName;
        return this;
    }

    public String getNodeName() {
        return nodeName;
    }

    public CustomAction setNodeName(String nodeName) {
        this.nodeName = nodeName;
        return this;
    }

    public Map<String, Object> toArray() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("name", name);
        out.put("url", url);
        out.put("action", action.value());
        out.put("body", body);
        out.put("topologyName", topologyName);
        out.put("nodeName", nodeName);
        return out;
    }
}
