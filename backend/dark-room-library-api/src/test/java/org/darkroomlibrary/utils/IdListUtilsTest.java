package org.darkroomlibrary.utils;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdListUtilsTest {

    @Test
    void detectsOversizedBatchAfterNormalization() {
        assertFalse(IdListUtils.exceedsBatchLimit(
                IdListUtils.normalize(IntStream.rangeClosed(1, 200).boxed().toList())
        ));
        assertTrue(IdListUtils.exceedsBatchLimit(
                IdListUtils.normalize(IntStream.rangeClosed(1, 201).boxed().toList())
        ));
    }
}
