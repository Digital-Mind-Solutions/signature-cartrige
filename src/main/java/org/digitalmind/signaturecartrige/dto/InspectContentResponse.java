package org.digitalmind.signaturecartrige.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Data
@EqualsAndHashCode
@ToString
public class InspectContentResponse {

    @SuperBuilder
    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    @EqualsAndHashCode
    @ToString
    public static class FieldPosition {
        private float page;
        private float left;
        private float bottom;
        private float right;
        private float top;
    }

    private boolean signatureOk;

    @Singular
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<String> signatureMissingFields;

    @Singular
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<String> signatureAdditionalFields;

    @Singular
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, float[]> signatureFieldDetails;

    private boolean privateVersion;

    @Singular
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<String> privateFields;

    @Singular
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<String> replaceFields;

    @JsonIgnore
    public List<String> getSignatureFields() {
        return this.signatureFieldDetails.keySet().stream().collect(Collectors.toList());
    }

}
