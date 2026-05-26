package com.aquiles.simpleportals.data;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;

public final class SelectionSession {

    private boolean selectorEnabled;
    private final List<Location> positions = new ArrayList<>();
    private long lastSelectionAt;

    public boolean isSelectorEnabled() {
        return selectorEnabled;
    }

    public void setSelectorEnabled(boolean selectorEnabled) {
        this.selectorEnabled = selectorEnabled;
    }

    public int addPosition(Location position) {
        for (int index = 0; index < positions.size(); index++) {
            Location existing = positions.get(index);
            if (sameBlock(existing, position)) {
                positions.set(index, position);
                return index + 1;
            }
        }
        positions.add(position);
        return positions.size();
    }

    public int removePosition(Location position) {
        for (int index = 0; index < positions.size(); index++) {
            Location existing = positions.get(index);
            if (sameBlock(existing, position)) {
                positions.remove(index);
                return index + 1;
            }
        }
        return 0;
    }

    public List<Location> getPositions() {
        return positions.stream().map(Location::clone).toList();
    }

    public int positionCount() {
        return positions.size();
    }

    public boolean hasWorldMismatch() {
        if (positions.size() < 2) {
            return false;
        }
        Location first = positions.get(0);
        if (first.getWorld() == null) {
            return true;
        }
        String worldName = first.getWorld().getName();
        return positions.stream().anyMatch(position -> position.getWorld() == null || !position.getWorld().getName().equalsIgnoreCase(worldName));
    }

    public long getLastSelectionAt() {
        return lastSelectionAt;
    }

    public void touchSelection() {
        this.lastSelectionAt = System.currentTimeMillis();
    }

    public void clearSelection() {
        positions.clear();
        this.lastSelectionAt = 0L;
    }

    public boolean hasSelection() {
        return !positions.isEmpty();
    }

    private boolean sameBlock(Location first, Location second) {
        return first.getWorld() != null
            && second.getWorld() != null
            && first.getWorld().getName().equalsIgnoreCase(second.getWorld().getName())
            && first.getBlockX() == second.getBlockX()
            && first.getBlockY() == second.getBlockY()
            && first.getBlockZ() == second.getBlockZ();
    }
}
