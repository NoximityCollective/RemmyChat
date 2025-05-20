package com.noximity.remmyChat.models;

public class GroupFormat {

    private final String name;
    private final String nameStyle;
    private final String prefix;
    private final String format;

    public GroupFormat(String name, String nameStyle, String prefix, String format) {
        this.name = name;
        this.nameStyle = nameStyle;
        this.prefix = prefix;
        this.format = format;
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

    public String getFormat() {
        return format;
    }
}
