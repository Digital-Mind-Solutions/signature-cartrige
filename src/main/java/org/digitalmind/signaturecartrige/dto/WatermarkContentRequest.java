package org.digitalmind.signaturecartrige.dto;

import lombok.*;
import lombok.experimental.SuperBuilder;

import java.io.InputStream;
import java.io.OutputStream;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Data
@EqualsAndHashCode
@ToString
public class WatermarkContentRequest {
    private InputStream inputStream;
    private OutputStream outputStream;
    private InputStream watermarkStream;
}
