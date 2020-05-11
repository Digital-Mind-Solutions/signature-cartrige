package org.digitalmind.signaturecartrige.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.*;
import java.util.stream.Collectors;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Data
//@EqualsAndHashCode
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
        this.replaceFields = inspectContentResponse.getReplaceFields() != null ? new ArrayList<>(inspectContentResponse.getReplaceFields()) : null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InspectContentResponse that = (InspectContentResponse) o;
        return signatureOk == that.signatureOk &&
                privateVersion == that.privateVersion &&
                Objects.equals(signatureMissingFields, that.signatureMissingFields) &&
                Objects.equals(signatureAdditionalFields, that.signatureAdditionalFields) &&
                mapEquals(signatureFieldDetails, that.signatureFieldDetails) &&
                Objects.equals(privateFields, that.privateFields) &&
                Objects.equals(replaceFields, that.replaceFields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(signatureOk, signatureMissingFields, signatureAdditionalFields, mapHashCode(signatureFieldDetails), privateVersion, privateFields, replaceFields);
    }

    private boolean mapEquals(Map<String, float[]> map1, Map<String, float[]> map2) {
        if (map1 == null && map2 == null) {
            return true;
        }
        if (map1 == null || map2 == null) {
            return false;
        }
        if (map1.size() != map2.size()) {
            return false;
        }
        return map1.entrySet().stream()
                .allMatch(e -> Arrays.equals(e.getValue(), map2.get(e.getKey())));
    }

    private int mapHashCode(Map<String, float[]> map) {
        int h = 0;
        Iterator<Map.Entry<String, float[]>> i = map.entrySet().iterator();
        while (i.hasNext())
            h += mapEntryHashCode(i.next());
        return h;
    }

    private final int mapEntryHashCode(Map.Entry<String, float[]> entry) {
        return Objects.hashCode(entry.getKey()) ^ Arrays.hashCode(entry.getValue());
    }

}
