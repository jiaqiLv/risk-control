package com.riskcontrol.graph.model;

import lombok.Builder;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
@Builder
public class GraphEdge {
    private String edgeId;
    private String sourceId;
    private String targetId;
    private String edgeType;
    private Map<String, String> properties;
    private Long createdAtMs;

    public static GraphEdge fromProto(fraud.graph.v1.Edge proto) {
        return GraphEdge.builder()
                .edgeId(proto.getEdgeId())
                .sourceId(proto.getSourceId())
                .targetId(proto.getTargetId())
                .edgeType(proto.getEdgeType())
                .properties(new HashMap<>(proto.getPropertiesMap()))
                .createdAtMs(proto.getCreatedAtMs())
                .build();
    }

    public fraud.graph.v1.Edge toProto() {
        return fraud.graph.v1.Edge.newBuilder()
                .setEdgeId(edgeId)
                .setSourceId(sourceId)
                .setTargetId(targetId)
                .setEdgeType(edgeType)
                .putAllProperties(properties)
                .setCreatedAtMs(createdAtMs)
                .build();
    }
}
