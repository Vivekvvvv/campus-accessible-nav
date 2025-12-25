package com.demo.accessiblenav.graph.dto;

import java.util.List;

public class GraphVersionDiff {
    private long fromVersion;
    private long toVersion;
    private int nodesAdded;
    private int nodesRemoved;
    private int nodesModified;
    private int edgesAdded;
    private int edgesRemoved;
    private int edgesModified;
    private List<String> diffEntries;

    public long getFromVersion() {
        return fromVersion;
    }

    public void setFromVersion(long fromVersion) {
        this.fromVersion = fromVersion;
    }

    public long getToVersion() {
        return toVersion;
    }

    public void setToVersion(long toVersion) {
        this.toVersion = toVersion;
    }

    public int getNodesAdded() {
        return nodesAdded;
    }

    public void setNodesAdded(int nodesAdded) {
        this.nodesAdded = nodesAdded;
    }

    public int getNodesRemoved() {
        return nodesRemoved;
    }

    public void setNodesRemoved(int nodesRemoved) {
        this.nodesRemoved = nodesRemoved;
    }

    public int getNodesModified() {
        return nodesModified;
    }

    public void setNodesModified(int nodesModified) {
        this.nodesModified = nodesModified;
    }

    public int getEdgesAdded() {
        return edgesAdded;
    }

    public void setEdgesAdded(int edgesAdded) {
        this.edgesAdded = edgesAdded;
    }

    public int getEdgesRemoved() {
        return edgesRemoved;
    }

    public void setEdgesRemoved(int edgesRemoved) {
        this.edgesRemoved = edgesRemoved;
    }

    public int getEdgesModified() {
        return edgesModified;
    }

    public void setEdgesModified(int edgesModified) {
        this.edgesModified = edgesModified;
    }

    public List<String> getDiffEntries() {
        return diffEntries;
    }

    public void setDiffEntries(List<String> diffEntries) {
        this.diffEntries = diffEntries;
    }
}
