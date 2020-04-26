package org.digitalmind.signaturecartrige.dto;

import lombok.Getter;

@Getter
public enum SignatureMode {
    TEXT("text"),
    IMAGE("image"),
    INVISIBLE("text");

    private String value;

    SignatureMode(String value) {
        this.value = value;
    }

}
