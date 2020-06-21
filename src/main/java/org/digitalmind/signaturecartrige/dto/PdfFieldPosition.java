package org.digitalmind.signaturecartrige.dto;

import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Data
@EqualsAndHashCode
@ToString
public class PdfFieldPosition {

    private float page;
    private float left;
    private float bottom;
    private float right;
    private float top;

}
