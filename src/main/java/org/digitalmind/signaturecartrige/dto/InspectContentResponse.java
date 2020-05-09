package org.digitalmind.signaturecartrige.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.ArrayList;
import java.util.Arrays;
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

    public InspectContentResponse(InspectContentResponse inspectContentResponse) {
        this.signatureOk = inspectContentResponse.isSignatureOk();
        this.signatureMissingFields = inspectContentResponse.getSignatureMissingFields() != null ? new ArrayList<>(inspectContentResponse.getSignatureMissingFields()) : null;
        this.signatureAdditionalFields = inspectContentResponse.getSignatureAdditionalFields() != null ? new ArrayList<>(inspectContentResponse.getSignatureAdditionalFields()) : null;
        this.signatureFieldDetails = inspectContentResponse.getSignatureFieldDetails() != null
                ? inspectContentResponse.getSignatureFieldDetails().entrySet().stream().collect(Collectors.toMap(entry -> entry.getKey(), entry -> Arrays.copyOf(entry.getValue(), entry.getValue().length)))
                : null;
        this.privateVersion = inspectContentResponse.isPrivateVersion();
        this.privateFields = inspectContentResponse.getPrivateFields() != null ? new ArrayList<>(inspectContentResponse.getPrivateFields()) : null;
        this.replaceFields(inspectContentResponse.getReplaceFields() != null ? new ArrayList<>(inspectContentResponse.getReplaceFields()) : null;
    }

}
