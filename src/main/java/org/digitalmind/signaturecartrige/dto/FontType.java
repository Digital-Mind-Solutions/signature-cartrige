package org.digitalmind.signaturecartrige.dto;

import lombok.*;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Data
@EqualsAndHashCode
@ToString
public class FontType {
    private String name;
    @Builder.Default
    private Integer style = 0;
    @Builder.Default
    private Float size = 20f;
    @Builder.Default
    private Integer left = 0;
    @Builder.Default
    private Integer right = 0;
    @Builder.Default
    private Integer top = 0;
    @Builder.Default
    private Integer bottom = 0;

}
