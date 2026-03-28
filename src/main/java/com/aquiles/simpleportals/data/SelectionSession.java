package com.aquiles.simpleportals.data;

import org.bukkit.Location;

public final class SelectionSession {

    private boolean selectorEnabled;
    private Location pos1;
    private Location pos2;
    private long lastSelectionAt;

    public boolean isSelectorEnabled() {
        return selectorEnabled;
    }

    public void setSelectorEnabled(boolean selectorEnabled) {
        this.selectorEnabled = selectorEnabled;
    }

    public Location getPos1() {
        return pos1;
    }

    public void setPos1(Location pos1) {
        this.pos1 = pos1;
    }

    public Location getPos2() {
        return pos2;
    }

    public void setPos2(Location pos2) {
        this.pos2 = pos2;
    }

    public long getLastSelectionAt() {
        return lastSelectionAt;
    }

    public void touchSelection() {
        this.lastSelectionAt = System.currentTimeMillis();
    }

    public void clearSelection() {
        this.pos1 = null;
        this.pos2 = null;
        this.lastSelectionAt = 0L;
    }

    public boolean hasSelection() {
        return pos1 != null || pos2 != null;
    }
}