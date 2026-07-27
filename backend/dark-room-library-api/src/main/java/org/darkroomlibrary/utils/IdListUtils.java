package org.darkroomlibrary.utils;

import java.util.List;
import java.util.Objects;

public final class IdListUtils {

    public static final int MAX_BATCH_SIZE = 200;

    private IdListUtils() {
    }

    public static List<Integer> normalize(List<Integer> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList();
    }

    public static boolean exceedsBatchLimit(List<Integer> ids) {
        return ids != null && ids.size() > MAX_BATCH_SIZE;
    }
}
