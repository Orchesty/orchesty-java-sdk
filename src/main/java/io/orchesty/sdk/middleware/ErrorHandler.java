package io.orchesty.sdk.middleware;

import io.javalin.http.Context;
import io.orchesty.sdk.exception.OnRepeatException;
import io.orchesty.sdk.exception.OnStopAndFailException;
import io.orchesty.sdk.http.Responses;
import io.orchesty.sdk.process.Headers;
import io.orchesty.sdk.process.ProcessDto;
import io.orchesty.sdk.process.ResultCode;
import io.orchesty.sdk.storage.Node;
import io.orchesty.sdk.storage.NodeRepository;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Translates exceptions thrown by a node into the correct Bridge response.
 * <ul>
 *   <li>{@link OnRepeatException} - a {@code REPEAT} success response; the interval/hops
 *       come from the node's {@code systemConfigs.repeater} when enabled, else from the
 *       exception.</li>
 *   <li>{@link OnStopAndFailException} - a {@code STOP_AND_FAILED} success response.</li>
 *   <li>anything else - a business error response.</li>
 * </ul>
 * A {@code null} {@link NodeRepository} disables the per-node repeater lookup (the
 * exception's own interval/hops are used).
 */
public class ErrorHandler {

    private final NodeRepository nodeRepository;

    public ErrorHandler(NodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
    }

    public void handle(Context ctx, ProcessDto dto, Throwable e) {
        if (e instanceof OnRepeatException repeat) {
            Node.Repeater repeater = lookupRepeater(dto);
            if (repeater != null && repeater.enabled()) {
                dto.setRepeater(repeater.interval(), repeater.hops(), repeat.getMessage());
            } else {
                dto.setRepeater(repeat.getInterval(), repeat.getMaxHops(), repeat.getMessage());
            }
            Responses.success(ctx, dto);
            return;
        }

        if (e instanceof OnStopAndFailException) {
            dto.setStopProcess(ResultCode.STOP_AND_FAILED, e.getMessage());
            Responses.success(ctx, dto);
            return;
        }

        Responses.error(ctx, dto, e);
    }

    private Node.Repeater lookupRepeater(ProcessDto dto) {
        if (nodeRepository == null) {
            return null;
        }
        String nodeId = dto.getHeader(Headers.NODE_ID);
        if (nodeId == null || nodeId.isEmpty()) {
            return null;
        }
        try {
            java.util.Map<String, Object> filter = new LinkedHashMap<>();
            filter.put("ids", List.of(nodeId));
            Node node = nodeRepository.findOne(filter);
            return node == null ? null : node.getRepeater();
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
