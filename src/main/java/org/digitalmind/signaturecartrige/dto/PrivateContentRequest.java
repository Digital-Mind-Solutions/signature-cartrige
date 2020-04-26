package org.digitalmind.signaturecartrige.dto;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.InputStream;
import java.util.List;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Data
@EqualsAndHashCode
@ToString
public class PrivateContentRequest {
    private InputStream inputStream;
    @Singular
    private List<String> privateFields;
//    @Singular
//    private List<String> privateTokens;
}
