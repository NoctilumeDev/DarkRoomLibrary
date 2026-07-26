package org.darkroomlibrary.utils;

import java.util.List;
import java.util.Objects;

public final class IdListUtils {

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
}
