package org.darkroomlibrary.domain.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;

@Getter
@AllArgsConstructor
public enum FileReferenceType {

    BOOK_COVER("book_cover", true),
    USER_AVATAR("user_avatar", true),
    MESSAGE_ATTACHMENT("msg_attachment", false),
    NOTICE_ASSET("notice_asset", true);

    private final String value;
    private final boolean publicAccess;

    public static FileReferenceType fromValue(String value) {
        return Arrays.stream(values())
                .filter(type -> type.value.equals(value))
                .findFirst()
                .orElse(null);
    }
}
