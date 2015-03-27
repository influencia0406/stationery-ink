package org.tommy.stationery.ink.enums;

/**
 * Created by kun7788 on 15. 1. 23..
 */
public enum SourceCatalogEnum {
    KAFKA("KAFKA"),
    RABBITMQ("RABBITMQ"),
    HDFS("HDFS"),
    PHOENIX("PHOENIX");

    private String name;

    public String getName() {
        return name;
    }


    private SourceCatalogEnum(String name) {
        this.name = name;
    }
}

