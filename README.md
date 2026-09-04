# JAVA Orchesty SDK

## How to use ?
- Install the package `io.orchesty:orchesty-java-sdk`
- Add an SLF4J backend to your worker, otherwise nothing is logged; e.g. `org.slf4j:slf4j-simple` logs to the console
- Import the classes ``import io.orchesty.sdk.WorkerConfig;``, ``import io.orchesty.sdk.WorkerServer;``, ``import io.orchesty.sdk.node.NodeRegistry;``
- First you will need to create the registry by calling `NodeRegistry registry = new NodeRegistry();`
- Then start the server using `new WorkerServer(WorkerConfig.fromEnv(), registry).start();`
  - `WorkerConfig.fromEnv()` reads the platform settings from environment variables: `TENANT_ID` (`local`, `docker` or your Cloud tenant), `CRYPT_SECRET` (required) and `ORCHESTY_API_KEY`
  - Optional: `MAX_REQUEST_BODY_MB` limits the request body size (default 50, the same as the tunnel proxy)
- In between you will need to create node
  - Example: `GetOrderDetail getOrderDetail = new GetOrderDetail();`
- Finally, you will need to register the node into the NodeRegistry and you can do that by using this method
  - Example: `registry.registerConnector(getOrderDetail);`

## How to develop
1. Run `make init` for start dev environment
2. Tests can be run by `make test` or `make fasttest`

## Audit checkpoints (Trace)

The SDK lets a node declare itself as a **business audit checkpoint** so the
[Trace](https://github.com/orchesty/orchesty/tree/master/trace) service can
return per-entity input/output snapshots (time, payload, status) without
duplicating large payloads in storage.

### Declaring a checkpoint

Implement `IAuditCheckpointProvider` on the node class and return the checkpoint
from `getAuditCheckpoint()`. It is a static metadata declaration — defined once on
the class, not inside `process`:

```java
import io.orchesty.sdk.node.AConnector;
import io.orchesty.sdk.node.IAuditCheckpoint;
import io.orchesty.sdk.node.IAuditCheckpointProvider;
import io.orchesty.sdk.process.ProcessDto;
import java.util.List;

public class EshopOrderReceiveConnector extends AConnector implements IAuditCheckpointProvider {

    @Override
    public String getName() {
        return "eshop-order-receive";
    }

    @Override
    public IAuditCheckpoint getAuditCheckpoint() {
        return new IAuditCheckpoint() {
            @Override
            public String getRole() {
                return "process_entry";
            }

            @Override
            public List<String> getFields() {
                return List.of("id", "trackingId");
            }
        };
    }

    @Override
    public ProcessDto process(ProcessDto dto) {
        return dto;
    }
}
```

The roles are the lowercase strings emitted on the wire (`process_entry`,
`process_step`, `process_exit`), so the bridge contract is the same as for
every other worker.

Allowed roles:

- `process_entry` — boundary node where business data **enters** the process
  from an external system (e-shop webhook receive, ERP poll, file drop).
- `process_step` — intermediate marker inside a business chain (e.g.
  enrichment, validation, transformation).
- `process_exit` — boundary node where the process **delegates** data to its
  destination (ERP create order, send confirmation e-mail).
- Returning `null` from `getAuditCheckpoint()` — neutral middle node, no audit
  emission.

For a pass-through node that only declares a checkpoint, extend
`AuditCheckpointNode` and implement `getName()` and `getAuditCheckpoint()`.

### Authoritative-entity rule

`audit-entity` should reference the entity **the node authoritatively
changes/transfers**, not every entity referenced anywhere in the body. In an
"Order to ERP" flow the connector audits `order`, not the products that are
line items inside the order.

### Per-item audit in batch nodes

In `ABatch` nodes, **do not** use `dto.addAuditHeader(...)` on the batch DTO.
After the bridge splits the batch, every child message would carry the union of
all audit headers, defeating per-entity Trace queries.

Pass a per-item `audit-entity` header instead, which scopes each entry to the
single item it represents. Start from the DTO's existing `audit-entity` header
and add the item's entity, so entities propagated by a parent split are kept:

```java
@Override
public BatchProcessDto process(BatchProcessDto dto) {
    for (Order o : fetchOrders()) {
        Map<String, Object> audit = Json.fromString(
                dto.getHeader("audit-entity", "{}"), new TypeReference<LinkedHashMap<String, Object>>() {});
        audit.put("order", Map.of("key", "id", "fields", List.of(Map.of("id", o.id(), "trackingId", o.trackingId()))));
        dto.addItem(o, null, null, Map.of("audit-entity", Json.toString(audit)));
    }
    return dto;
}
```

For items that legitimately and authoritatively touch more than one entity,
put one entry per entity name into the same map:

```java
audit.put("order", Map.of("key", "id", "fields", List.of(Map.of("id", it.orderId()))));
audit.put("invoice", Map.of("key", "id", "fields", List.of(Map.of("id", it.invoiceId()))));
dto.addItem(it, null, null, Map.of("audit-entity", Json.toString(audit)));
```

The bridge splits the batch into one message per item, each carrying only its
own `audit-entity` header.

Because each item starts from the DTO's existing `audit-entity` header, batches
in the middle of a chain stay correct: when a parent split has already
propagated a per-item `audit-entity` (via `CopyBatchItem`), those parent
entities are preserved. On conflict the new entry replaces the previous entry
for that entity name (per-entity, last-write-wins).
