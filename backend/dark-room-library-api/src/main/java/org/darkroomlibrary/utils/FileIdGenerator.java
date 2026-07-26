package org.darkroomlibrary.utils;

import java.security.SecureRandom;
import java.util.HexFormat;

public final class FileIdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private FileIdGenerator() {
    }

    public static String nextId() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }
}
