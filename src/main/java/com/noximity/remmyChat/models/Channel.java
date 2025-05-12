package com.noximity.remmyChat.models;

public class Channel {

    private final String name;
    private final String format;
    private final String permission;
    private final double radius;

    public Channel(String name, String format, String permission, double radius) {
        this.name = name;
        this.format = format;
        this.permission = permission;
        this.radius = radius;
    }

    public String getName() {
        return name;
    }

    public String getFormat() {
        return format;
    }

    public String getPermission() {
        return permission;
    }

    public double getRadius() {
        return radius;
    }
}