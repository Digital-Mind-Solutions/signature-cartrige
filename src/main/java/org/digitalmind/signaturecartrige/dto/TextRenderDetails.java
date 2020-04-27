package org.digitalmind.signaturecartrige.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.awt.*;

@NoArgsConstructor
@AllArgsConstructor
@Data
public class TextRenderDetails {
    private int width;
    private int height;
    private FontType fontType;
    private Color backgroundColor;
    private Color transparentColor;
    private Color foregroundColor;
}
