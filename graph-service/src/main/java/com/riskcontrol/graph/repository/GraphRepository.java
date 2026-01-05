package com.riskcontrol.graph.repository;

import com.riskcontrol.graph.model.GraphEdge;
import com.riskcontrol.graph.model.GraphNode;

import java.util.List;
import java.util.Optional;

public interface GraphRepository {

    void upsertNode(GraphNode node);

    void upsertEdge(GraphEdge edge);

    Optional<GraphNode> getNode(String nodeId);

    Optional<GraphEdge> getEdge(String edgeId);

    List<GraphNode> getNeighbors(String nodeId, String edgeType, int limit);

    List<GraphEdge> getEdges(String sourceId, String targetId, String edgeType, long startTimeMs, long endTimeMs);
}
