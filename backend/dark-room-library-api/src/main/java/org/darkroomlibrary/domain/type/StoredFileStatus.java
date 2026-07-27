package org.darkroomlibrary.domain.type;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum StoredFileStatus {

    TEMPORARY(0),
    BOUND(1),
    DELETE_PENDING(2),
    DELETING(3);

    private final Integer status;
}
