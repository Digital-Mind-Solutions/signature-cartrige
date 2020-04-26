package org.digitalmind.signaturecartrige.dto;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.List;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Data
@EqualsAndHashCode
@ToString
public class SignatureCartridgeRequest {
    private String session;
    private Object signature;
    @Singular("trace")
    private List<String> trace;
    private String date;
    private SignatureConfigurationRequest configuration;
}
