package io.orchesty.sdk.di;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.orchesty.sdk.node.ABatch;
import io.orchesty.sdk.node.AConnector;
import io.orchesty.sdk.node.ICustomNode;
import io.orchesty.sdk.node.NodeRegistry;
import io.orchesty.sdk.process.BatchProcessDto;
import io.orchesty.sdk.process.ProcessDto;
import io.orchesty.sdk.transport.HttpSender;
import org.junit.jupiter.api.Test;

class DIContainerTest {

    private static final class SampleConnector extends AConnector {
        @Override
        public String getName() {
            return "sample-connector";
        }

        @Override
        public ProcessDto process(ProcessDto dto) {
            return dto;
        }

        HttpSender exposedSender() {
            return getSender();
        }
    }

    private static final class SampleBatch extends ABatch {
        @Override
        public String getName() {
            return "sample-batch";
        }

        @Override
        public BatchProcessDto process(BatchProcessDto dto) {
            return dto;
        }
    }

    private static final class SampleCustomNode implements ICustomNode {
        @Override
        public String getName() {
            return "sample-custom";
        }

        @Override
        public ProcessDto process(ProcessDto dto) {
            return dto;
        }
    }

    @Test
    void setNodeDispatchesByTypeIntoRegistry() {
        DIContainer container = new DIContainer();
        container.setNode(new SampleConnector());
        container.setNode(new SampleBatch());
        container.setNode(new SampleCustomNode());

        NodeRegistry registry = container.getRegistry();
        assertEquals(
                "sample-connector", registry.getConnector("sample-connector").getName());
        assertEquals("sample-batch", registry.getBatch("sample-batch").getName());
        assertEquals("sample-custom", registry.getCustomNode("sample-custom").getName());
    }

    @Test
    void setNodeInjectsHttpSenderWhenPresent() {
        HttpSender sender = new HttpSender();
        DIContainer container = new DIContainer().setHttpSender(sender);

        SampleConnector connector = new SampleConnector();
        container.setNode(connector);

        assertSame(sender, connector.exposedSender());
    }

    @Test
    void setNodeRejectsUnknownType() {
        DIContainer container = new DIContainer();
        assertThrows(IllegalArgumentException.class, () -> container.setNode("not a node"));
    }
}
