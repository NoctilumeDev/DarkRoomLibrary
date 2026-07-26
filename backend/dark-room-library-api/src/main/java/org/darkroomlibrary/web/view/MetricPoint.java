package org.darkroomlibrary.web.view;

import java.util.Objects;

/**
 * Named integer value consumed by dashboard and trend charts.
 */
public final class MetricPoint {

    private final String name;
    private final Integer count;

    public MetricPoint(String name, Integer count) {
        this.name = Objects.requireNonNullElse(name, "");
        this.count = count == null ? 0 : count;
    }

    public String getName() {
        return name;
    }

    public Integer getCount() {
        return count;
    }
}
