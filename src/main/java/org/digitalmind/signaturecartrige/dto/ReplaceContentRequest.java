package org.digitalmind.signaturecartrige.dto;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Data
@EqualsAndHashCode
@ToString
public class ReplaceContentRequest {
    private InputStream inputStream;
    private OutputStream outputStream;
    @Singular
    private Map<String, String> formFields;
//    @Singular
//    private Map<String, String> tokens;
}
