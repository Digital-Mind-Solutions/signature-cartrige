package org.digitalmind.signaturecartrige.dto;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Data
@EqualsAndHashCode
@ToString
public class FlattenContentRequest {
    private InputStream inputStream;
    private OutputStream outputStream;
    private List<String> flattenFields;
    private List<String> nonFlattenFields;
    private Boolean flattenSignatureFields;
}
