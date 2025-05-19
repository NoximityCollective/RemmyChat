package com.noximity.remmyChat.models;

public class GroupFormat {

    private final String name;
    private final String nameStyle;
    private final String prefix;

    public GroupFormat(String name, String nameStyle, String prefix) {
        this.name = name;
        this.nameStyle = nameStyle;
        this.prefix = prefix;
    }

    public String getName() {
        return name;
    }

    public String getNameStyle() {
        return nameStyle;
    }

    public String getPrefix() {
        return prefix;
    }
}
