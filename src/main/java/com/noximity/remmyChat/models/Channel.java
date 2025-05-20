package com.noximity.remmyChat.models;

public class Channel {

    private final String name;
    private final String permission;
    private final double radius;
    private final String prefix;
    private final String hover;
    private final String displayName;

    public Channel(String name, String permission, double radius, String prefix, String hover, String displayName) {
        this.name = name;
        this.permission = permission;
        this.radius = radius;
        this.prefix = prefix;
        this.hover = hover;
        this.displayName = displayName;
    }

    public String getName() {
        return name;
    }

    public String getPermission() {
        return permission;
    }

    public double getRadius() {
        return radius;
    }

    public String getPrefix() {
        return prefix;
    }

    public String getHover() {
        return hover;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean hasDisplayName() {
        return displayName != null && !displayName.isEmpty();
    }
}
