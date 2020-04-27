package org.digitalmind.signaturecartrige.dto;

import lombok.*;
import lombok.experimental.SuperBuilder;
import org.digitalmind.signaturecartrige.enumeration.SignatureFieldAppearance;
import org.digitalmind.signaturecartrige.enumeration.SignatureImageType;

import java.awt.*;

@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Data
@EqualsAndHashCode
@ToString
public class SignatureConfiguration {
    private SignatureMode mode;
    private SignatureFieldAppearance signatureFieldAppearance;
    private Color backgroundColor;
    private Color transparentColor;
    private Color foregroundColor;
    private Color borderColor;
    private Font sessionFont;
    private FontType sessionFontType;
    private Color sessionColor;
    private String sessionLabel;
    private Font signatureFont;
    private FontType signatureFontType;
    private Color signatureColor;
    private Font traceFont;
    private FontType traceFontType;
    private Color traceColor;
    private Font dateFont;
    private FontType dateFontType;
    private String dateLabel;
    private Color dateColor;

    private Integer newWidth;
    private Integer newHeight;

    private SignatureImageType imageType;
}
