package org.digitalmind.signaturecartrige.enumeration;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Data
@EqualsAndHashCode
@ToString
public class SignatureFieldAppearance {

    public static final SignatureFieldAppearance FULL = SignatureFieldAppearance.builder().session(true).signature(true).trace(true).date(true).build();
    public static final SignatureFieldAppearance DEFAULT = FULL;

    private Boolean border;
    private Boolean session;
    private Boolean signature;
    private Boolean trace;
    private Boolean date;

    @JsonIgnore
    public boolean hasBorder() {
        return Boolean.TRUE.equals(border);
    }

    @JsonIgnore
    public boolean hasSession() {
        return Boolean.TRUE.equals(session);
    }

    @JsonIgnore
    public boolean hasSignature() {
        return Boolean.TRUE.equals(signature);
    }

    @JsonIgnore
    public boolean hasTrace() {
        return Boolean.TRUE.equals(trace);
    }

    @JsonIgnore
    public boolean hasDate() {
        return Boolean.TRUE.equals(date);
    }

}
