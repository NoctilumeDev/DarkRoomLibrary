package org.darkroomlibrary.web.response;

public enum ApiStatusCode {
    SUCCESS(200),
    BAD_REQUEST(400);

    private final int value;

    ApiStatusCode(int value) {
        this.value = value;
    }

    public int value() {
        return value;
    }
}
