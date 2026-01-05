package com.riskcontrol.graph.repository;

import com.riskcontrol.graph.model.GraphEdge;
import com.riskcontrol.graph.model.GraphNode;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Repository
public class InMemoryGraphRepository implements GraphRepository {

    private final Map<String, GraphNode> nodes = new ConcurrentHashMap<>();
    private final Map<String, GraphEdge> edges = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> adjacencyList = new ConcurrentHashMap<>(); // nodeId -> set of edgeIds

    @Override
    public void upsertNode(GraphNode node) {
        nodes.put(node.getNodeId(), node);
    }

    @Override
    public void upsertEdge(GraphEdge edge) {
        edges.put(edge.getEdgeId(), edge);

        // Update adjacency list
        adjacencyList.computeIfAbsent(edge.getSourceId(), k -> ConcurrentHashMap.newKeySet())
                .add(edge.getEdgeId());
        adjacencyList.computeIfAbsent(edge.getTargetId(), k -> ConcurrentHashMap.newKeySet())
                .add(edge.getEdgeId());
    }

    @Override
    public Optional<GraphNode> getNode(String nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }

    @Override
    public Optional<GraphEdge> getEdge(String edgeId) {
        return Optional.ofNullable(edges.get(edgeId));
    }

    @Override
    public List<GraphNode> getNeighbors(String nodeId, String edgeType, int limit) {
        Set<String> edgeIds = adjacencyList.getOrDefault(nodeId, Collections.emptySet());

        return edgeIds.stream()
                .limit(limit)
                .map(edges::get)
                .filter(Objects::nonNull)
                .filter(edge -> edgeType == null || edgeType.isEmpty() || edge.getEdgeType().equals(edgeType))
                .map(edge -> {
                    if (edge.getSourceId().equals(nodeId)) {
                        return nodes.get(edge.getTargetId());
                    } else {
                        return nodes.get(edge.getSourceId());
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<GraphEdge> getEdges(String sourceId, String targetId, String edgeType,
                                   long startTimeMs, long endTimeMs) {
        return edges.values().stream()
                .filter(edge -> sourceId == null || sourceId.isEmpty() || edge.getSourceId().equals(sourceId))
                .filter(edge -> targetId == null || targetId.isEmpty() || edge.getTargetId().equals(targetId))
                .filter(edge -> edgeType == null || edgeType.isEmpty() || edge.getEdgeType().equals(edgeType))
                .filter(edge -> edge.getCreatedAtMs() >= startTimeMs && edge.getCreatedAtMs() <= endTimeMs)
                .collect(Collectors.toList());
    }
}
