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
public class AddSignatureContentRequest {
    private InputStream inputStream;
    private OutputStream outputStream;
    private Map<String, PdfFieldPosition> signatureFields;
}
