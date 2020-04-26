package org.digitalmind.signaturecartrige.dto;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.digitalmind.signaturecartrige.enumeration.SignatureFieldAppearance;
import org.digitalmind.signaturecartrige.enumeration.SignatureImageType;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Data
@EqualsAndHashCode
@ToString
public class SignatureConfigurationRequest {
    private SignatureMode mode;
    private SignatureFieldAppearance signatureFieldAppearance;
    @Builder.Default
    private String backgroundColor = "white";
    @Builder.Default
    private String foregroundColor = "black";
    private String borderColor;
    private FontType sessionFontType;
    private String sessionColor;
    private String sessionLabel;
    private FontType signatureFontType;
    private String signatureColor;
    private FontType traceFontType;
    private String traceColor;
    private FontType dateFontType;
    private String dateLabel;
    private String dateColor;

    private Integer newWidth;
    private Integer newHeight;

    private SignatureImageType imageType;
}
