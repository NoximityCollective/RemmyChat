package com.noximity.remmyChat.models;

public class Channel {

    private final String name;
    private final String permission;
    private final double radius;
    private final String prefix;
    private final String hover;

    public Channel(String name, String permission, double radius, String prefix, String hover) {
        this.name = name;
        this.permission = permission;
        this.radius = radius;
        this.prefix = prefix;
        this.hover = hover;
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
}

