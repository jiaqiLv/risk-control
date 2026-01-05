package com.riskcontrol.graph.model;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
public class GraphNode {
    private String nodeId;
    private String nodeType;
    private Map<String, String> properties;
    private Long createdAtMs;
    private Long updatedAtMs;

    public static GraphNode fromProto(fraud.graph.v1.Node proto) {
        return GraphNode.builder()
                .nodeId(proto.getNodeId())
                .nodeType(proto.getNodeType())
                .properties(new HashMap<>(proto.getPropertiesMap()))
                .createdAtMs(proto.getCreatedAtMs())
                .updatedAtMs(proto.getUpdatedAtMs())
                .build();
    }

    public fraud.graph.v1.Node toProto() {
        return fraud.graph.v1.Node.newBuilder()
                .setNodeId(nodeId)
                .setNodeType(nodeType)
                .putAllProperties(properties)
                .setCreatedAtMs(createdAtMs)
                .setUpdatedAtMs(updatedAtMs)
                .build();
    }
}
