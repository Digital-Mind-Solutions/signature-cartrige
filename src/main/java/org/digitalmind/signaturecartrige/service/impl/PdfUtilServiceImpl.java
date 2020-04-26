package org.digitalmind.signaturecartrige.service.impl;

import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.beryx.awt.color.ColorFactory;
import org.digitalmind.signaturecartrige.dto.*;
import org.digitalmind.signaturecartrige.enumeration.SignatureFieldAppearance;
import org.digitalmind.signaturecartrige.exception.PdfUtilException;
import org.digitalmind.signaturecartrige.sam.SignatureCartridgeRenderer;
import org.digitalmind.signaturecartrige.sam.impl.SignatureCartridgeRendererImpl;
import org.digitalmind.signaturecartrige.service.PdfUtilService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.digitalmind.signaturecartrige.config.PdfUtilModuleConfig.ENABLED;

@Service
@ConditionalOnProperty(name = ENABLED, havingValue = "true")
@Slf4j
public class PdfUtilServiceImpl implements PdfUtilService {

    private static final int IMAGE_TYPE = BufferedImage.TYPE_INT_ARGB;
    private Map<SignatureConfigurationRequest, SignatureConfiguration> signatureConfigurationMap = new ConcurrentHashMap<>();
    private Map<String, Font> fontMap = new ConcurrentHashMap<>();

    @Override
    public InspectContentResponse inspect(InspectContentRequest request) throws IOException {
        Assert.notNull(request, this.getClass().getSimpleName() + ".validateSignatureFields: ValidateSignatureRequest must not be null");
        Assert.notNull(request.getInputStream(), this.getClass().getSimpleName() + ".validateSignatureFields: Pdf stream must not be null");
        InspectContentResponse.InspectContentResponseBuilder builder = InspectContentResponse.builder();

        List<String> requestFieldNameOrPatternList = request.getSignatureFields() != null ? request.getSignatureFields() : Collections.emptyList();
        try (PdfReader reader = new PdfReader(request.getInputStream())) {
            AcroFields acroFields = reader.getAcroFields();
            List<String> signatureFieldNames = acroFields.getFieldNamesWithBlankSignatures();
            signatureFieldNames.forEach(signatureFieldName -> {
                float[] pos = acroFields.getFieldPositions(signatureFieldName);
                builder.signatureFieldDetail(signatureFieldName, pos);
            });
            builder.signatureOk(true);

            signatureFieldNames.forEach(pdfFieldName -> {
                boolean matches = false;
                for (String requestFieldNameOrPattern : requestFieldNameOrPatternList) {
                    if (match(requestFieldNameOrPattern, pdfFieldName)) {
                        matches = true;
                        break;
                    }
                }
                if (!matches) {
                    builder.signatureAdditionalField(pdfFieldName);
                    builder.signatureOk(false);
                }
            });

            requestFieldNameOrPatternList.forEach(requestFieldNameOrPattern -> {
                boolean matches = false;
                for (String pdfFieldName : signatureFieldNames) {
                    if (match(requestFieldNameOrPattern, pdfFieldName)) {
                        matches = true;
                        break;
                    }
                }
                if (!matches) {
                    builder.signatureMissingField(requestFieldNameOrPattern);
                    builder.signatureOk(false);
                }
            });

            Set<String> allFieldNames = acroFields.getAllFields().keySet();

            builder.privateVersion(false);
            if (request.getPrivateFields() != null && allFieldNames.size() > 0) {
                for (String fieldNameOrPattern : request.getPrivateFields()) {
                    Set<String> fieldNamesInPdf = null;
                    if (fieldNameOrPattern.contains("*") || fieldNameOrPattern.contains("?")) {
                        fieldNamesInPdf = allFieldNames.stream().filter(fieldName -> match(fieldNameOrPattern, fieldName)).collect(Collectors.toSet());
                    } else {
                        fieldNamesInPdf = allFieldNames.stream().filter(fieldName -> fieldNameOrPattern.equals(fieldName)).collect(Collectors.toSet());
                    }
                    if (fieldNamesInPdf != null && fieldNamesInPdf.size() > 0) {
                        builder.privateVersion(true);
                        fieldNamesInPdf.forEach(fieldName -> builder.privateField(fieldName));
                    }
                }
            }

            if (request.getReplaceFields() != null && allFieldNames.size() > 0) {
                for (String fieldNameOrPattern : request.getReplaceFields()) {
                    Set<String> fieldNamesInPdf = null;
                    if (fieldNameOrPattern.contains("*") || fieldNameOrPattern.contains("?")) {
                        fieldNamesInPdf = allFieldNames.stream().filter(fieldName -> match(fieldNameOrPattern, fieldName)).collect(Collectors.toSet());
                    } else {
                        fieldNamesInPdf = allFieldNames.stream().filter(fieldName -> fieldNameOrPattern.equals(fieldName)).collect(Collectors.toSet());
                    }
                    if (fieldNamesInPdf != null && fieldNamesInPdf.size() > 0) {
                        fieldNamesInPdf.forEach(fieldName -> builder.replaceField(fieldName));
                    }
                }
            } else {
                builder.replaceFields(allFieldNames);
            }

            return builder.build();
        }
    }

    @Override
    public ReplaceContentResponse replace(ReplaceContentRequest request) throws IOException {
        Assert.notNull(request.getInputStream(), this.getClass().getSimpleName() + ".validateSignatureFields: Pdf stream must not be null");
        ReplaceContentResponse response = new ReplaceContentResponse();
        PdfStamper stamper = null;
        try (PdfReader reader = new PdfReader(request.getInputStream())) {
            stamper = new PdfStamper(reader, request.getOutputStream(), '\0', false);
            AcroFields acroFields = stamper.getAcroFields();
            Set<String> fieldNames = acroFields.getAllFields().keySet();
            if (request.getFormFields() != null && fieldNames.size() > 0) {
                for (Map.Entry<String, String> entry : request.getFormFields().entrySet()) {
                    String fieldNameOrPattern = entry.getKey();
                    Set<String> fieldNamesInPdf = null;
                    if (fieldNameOrPattern.contains("*") || fieldNameOrPattern.contains("?")) {
                        fieldNamesInPdf = fieldNames.stream().filter(fieldName -> match(fieldNameOrPattern, fieldName)).collect(Collectors.toSet());
                    } else {
                        fieldNamesInPdf = fieldNames.stream().filter(fieldName -> fieldNameOrPattern.equals(fieldName)).collect(Collectors.toSet());
                    }
                    for (String fieldName : fieldNamesInPdf) {
                        String fieldValue = acroFields.getField(entry.getKey());
                        if (fieldValue != null) {
                            acroFields.setField(entry.getKey(), entry.getValue());
                            //acroFields.setFieldProperty(entry.getKey(), "setfflags", PdfFormField.FF_READ_ONLY, null);
                        }
                    }
                }
            }
            // close pdf stamper
            //stamper.setFormFlattening(true);
        } finally {
            stamper.close();
        }
        return response;
    }

    public WatermarkContentResponse watermark(WatermarkContentRequest request) throws IOException {
        Assert.notNull(request.getInputStream(), this.getClass().getSimpleName() + ".watermark: Pdf input stream must not be null");
        Assert.notNull(request.getOutputStream(), this.getClass().getSimpleName() + ".watermark: Pdf output stream must not be null");
        Assert.notNull(request.getWatermarkStream(), this.getClass().getSimpleName() + ".watermark: Pdf watermark stream must not be null");
        WatermarkContentResponse response = new WatermarkContentResponse();
        PdfStamper stamper = null;
        try (PdfReader reader = new PdfReader(request.getInputStream());
        ) {
            stamper = new PdfStamper(reader, request.getOutputStream(), '\0', false);
            stamper.setFormFlattening(true);
            int n = reader.getNumberOfPages();
            int i = 0;
            PdfContentByte under;
            PdfContentByte over;
            byte[] watermarkImageByeArray = IOUtils.toByteArray(request.getWatermarkStream());
            com.lowagie.text.Image image = com.lowagie.text.Image.getInstance(watermarkImageByeArray);
            image.scaleToFit(PageSize.A5.getWidth(), PageSize.A5.getHeight());
            while (i < n) {
                i++;
                under = stamper.getUnderContent(i);
                over = stamper.getOverContent(i);
                float x = (reader.getPageSize(i).getWidth() - image.getScaledWidth()) / 2;
                float y = (reader.getPageSize(i).getHeight() - image.getScaledHeight()) / 2;
                image.setAbsolutePosition(x, y);

                over.saveState();
                PdfGState state = new PdfGState();
                state.setFillOpacity(0.5f);
                over.setGState(state);
                over.addImage(image);
                over.restoreState();
            }
        } finally {
            if (stamper != null) {
                stamper.close();
            }
        }
        return response;
    }

    @Override
    public FlattenContentResponse flatten(FlattenContentRequest request) throws IOException {
        Assert.notNull(request.getInputStream(), this.getClass().getSimpleName() + ".validateSignatureFields: Pdf stream must not be null");
        FlattenContentResponse response = new FlattenContentResponse();
        PdfStamper stamper = null;
        try (PdfReader reader = new PdfReader(request.getInputStream())) {
            stamper = new PdfStamper(reader, request.getOutputStream(), '\0', false);
            // close pdf stamper
            stamper.setFormFlattening(true);
        } finally {
            stamper.close();
        }
        return response;
    }

//    @Override
//    public PrivateContentResponse hasPrivateContent(PrivateContentRequest request) throws IOException {
//        Assert.notNull(request.getInputStream(), this.getClass().getSimpleName() + ".validateSignatureFields: Pdf stream must not be null");
//        PrivateContentResponse response = new PrivateContentResponse();
//        boolean privateFlag = false;
//        try (PdfReader reader = new PdfReader(request.getInputStream())) {
//            AcroFields acroFields = reader.getAcroFields();
//            Set<String> fieldNames = acroFields.getAllFields().keySet();
//            if (request.getPrivateFields() != null && fieldNames.size() > 0) {
//                for (String fieldNameOrPattern : request.getPrivateFields()) {
//                    Set<String> fieldNamesInPdf = null;
//                    if (fieldNameOrPattern.contains("*") || fieldNameOrPattern.contains("?")) {
//                        fieldNamesInPdf = fieldNames.stream().filter(fieldName -> match(fieldNameOrPattern, fieldName)).collect(Collectors.toSet());
//                    } else {
//                        fieldNamesInPdf = fieldNames.stream().filter(fieldName -> fieldNameOrPattern.equals(fieldName)).collect(Collectors.toSet());
//                    }
//                    if (fieldNamesInPdf != null && fieldNamesInPdf.size() > 0) {
//                        privateFlag = true;
//                        break;
//                    }
//                }
//            }
//        }
//        response.setPrivateFlag(privateFlag);
//        return response;
//    }

    //https://www.geeksforgeeks.org/wildcard-character-matching/
    //https://www.geeksforgeeks.org/wildcard-pattern-matching-three-symbols/?ref=rp
    public boolean match(String fieldNameOrPattern, String fieldName) {
        if (fieldNameOrPattern == null || fieldName == null) {
            return false;
        }
        fieldNameOrPattern = fieldNameOrPattern.toLowerCase();
        fieldName = fieldName.toLowerCase();

        // If we reach at the end of both strings,
        // we are done
        if (fieldNameOrPattern.length() == 0 && fieldName.length() == 0)
            return true;

        // Make sure that the characters after '*'
        // are present in second string.
        // This function assumes that the first
        // string will not contain two consecutive '*'
        if (fieldNameOrPattern.length() > 1 && fieldNameOrPattern.charAt(0) == '*' &&
                fieldName.length() == 0)
            return false;

        // If the first string contains '?',
        // or current characters of both strings match
        if ((fieldNameOrPattern.length() > 1 && fieldNameOrPattern.charAt(0) == '?') ||
                (fieldNameOrPattern.length() != 0 && fieldName.length() != 0 &&
                        fieldNameOrPattern.charAt(0) == fieldName.charAt(0)))
            return match(fieldNameOrPattern.substring(1),
                    fieldName.substring(1));

        // If there is *, then there are two possibilities
        // a) We consider current character of second string
        // b) We ignore current character of second string.
        if (fieldNameOrPattern.length() > 0 && fieldNameOrPattern.charAt(0) == '*')
            return match(fieldNameOrPattern.substring(1), fieldName) ||
                    match(fieldNameOrPattern, fieldName.substring(1));
        return false;
    }

    @Override
    public boolean match(String fieldNameOrPattern, List<String> fieldNames) {
        if (fieldNames == null || fieldNames.size() == 0 || fieldNameOrPattern == null) {
            return false;
        }

        return fieldNames.stream()
                .filter(fieldName -> match(fieldNameOrPattern, fieldName))
                .findFirst()
                .isPresent();
    }

    @Override
    public SignatureCartridgeResponse createSignatureImage(SignatureCartridgeRequest signatureCartridgeRequest) throws PdfUtilException {
        return createSignatureImage(signatureCartridgeRequest, null, null);
    }

    @Override
    public SignatureCartridgeResponse createSignatureImage(SignatureCartridgeRequest signatureCartridgeRequest, Integer width, Integer height) throws PdfUtilException {
        SignatureCartridgeResponse signatureCartridgeResponse = null;
        Integer finalWidth = width != null ? width : signatureCartridgeRequest.getConfiguration().getNewWidth();
        Integer finalHeight = height != null ? height : signatureCartridgeRequest.getConfiguration().getNewHeight();

        int cummulatedHeight = 0;
        try {
            ByteArrayOutputStream signatureImageStream;
            ByteArrayResource resource;

            signatureImageStream = new ByteArrayOutputStream();

            BufferedImage signatureImage = null;
            SignatureConfiguration configuration = null;
            try {
                configuration = getSignatureConfiguration(signatureCartridgeRequest.getConfiguration());
            } catch (IOException | FontFormatException e) {
                throw new PdfUtilException("Unable to load signature configuration", e);
            }
            SignatureFieldAppearance signatureFieldAppearance = configuration.getSignatureFieldAppearance();
            List<BufferedImage> bufferedImageList = new ArrayList<>();

            //--------------------------------------------------------------------------------------------------------------
            BufferedImage bufferedImageSession = null;
            if (signatureFieldAppearance.hasSession()) {
                bufferedImageSession = toImage(
                        configuration.getSessionLabel() + signatureCartridgeRequest.getSession(),
                        configuration.getSessionFont(), configuration.getBackgroundColor(), configuration.getSessionColor() != null ? configuration.getSessionColor() : configuration.getForegroundColor(),
                        configuration.getSessionFontType().getLeft(), configuration.getSessionFontType().getRight(), configuration.getSessionFontType().getTop(), configuration.getSessionFontType().getBottom(),
                        finalWidth, null,
                        null, null

                );
                cummulatedHeight += bufferedImageSession.getHeight();
            }


            //--------------------------------------------------------------------------------------------------------------
            BufferedImage bufferedImageTrace = null;
            if (signatureFieldAppearance.hasTrace()) {
                bufferedImageTrace = toImage(
                        signatureCartridgeRequest.getTrace(),
                        configuration.getTraceFont(), configuration.getBackgroundColor(), configuration.getTraceColor() != null ? configuration.getTraceColor() : configuration.getForegroundColor(),
                        configuration.getTraceFontType().getLeft(), configuration.getTraceFontType().getRight(), configuration.getTraceFontType().getTop(), configuration.getTraceFontType().getBottom(),
                        finalWidth, null,
                        null, null
                );
                cummulatedHeight += bufferedImageTrace.getHeight();
            }

            //--------------------------------------------------------------------------------------------------------------
            BufferedImage bufferedImageDate = null;
            if (signatureFieldAppearance.hasDate()) {
                bufferedImageDate = toImage(
                        configuration.getDateLabel() + signatureCartridgeRequest.getDate(),
                        configuration.getDateFont(), configuration.getBackgroundColor(), configuration.getDateColor() != null ? configuration.getDateColor() : configuration.getForegroundColor(),
                        configuration.getDateFontType().getLeft(), configuration.getDateFontType().getRight(), configuration.getDateFontType().getTop(), configuration.getDateFontType().getBottom(),
                        finalWidth, null,
                        null, null
                );
                cummulatedHeight += bufferedImageDate.getHeight();
            }

            //--------------------------------------------------------------------------------------------------------------
            BufferedImage bufferedImageSignature = null;
            boolean bufferedImageSignatureNeedsCorrection = true;
            if (signatureFieldAppearance.hasSignature()) {
                //this field can be text or image
                Integer signatureHeight = finalHeight != null && (finalHeight - cummulatedHeight) >= 0
                        ? finalHeight - cummulatedHeight
                        : finalHeight;
                if (signatureCartridgeRequest.getSignature() instanceof String) {
                    bufferedImageSignature = toImage(
                            (String) signatureCartridgeRequest.getSignature(),
                            configuration.getSignatureFont(), configuration.getBackgroundColor(), configuration.getSignatureColor() != null ? configuration.getSignatureColor() : configuration.getForegroundColor(),
                            configuration.getSignatureFontType().getLeft(), configuration.getSignatureFontType().getRight(), configuration.getSignatureFontType().getTop(), configuration.getSignatureFontType().getBottom(),
                            finalWidth, signatureHeight,
                            finalWidth, signatureHeight
                    );
                    bufferedImageSignatureNeedsCorrection = false;
                }

                if (signatureCartridgeRequest.getSignature() instanceof MultipartFile) {
                    try {
                        bufferedImageSignature = ImageIO.read(((MultipartFile) signatureCartridgeRequest.getSignature()).getInputStream());
                    } catch (IOException e) {
                        throw new PdfUtilException("Unable to load signature file into a buffered image", e);
                    }
                }


                if (signatureCartridgeRequest.getSignature() instanceof InputStream) {
                    try {
                        bufferedImageSignature = ImageIO.read((InputStream) signatureCartridgeRequest.getSignature());
                    } catch (IOException e) {
                        throw new PdfUtilException("Unable to load signature input stream into a buffered image", e);
                    }
                }

                if (signatureCartridgeRequest.getSignature() instanceof File) {
                    try {
                        bufferedImageSignature = ImageIO.read((File) signatureCartridgeRequest.getSignature());
                    } catch (IOException e) {
                        throw new PdfUtilException("Unable to load signature file into a buffered image", e);
                    }
                }

                if (bufferedImageSignature == null) {
                    throw new PdfUtilException("Unsupported signature content request signature type");
                } else {
                    if (bufferedImageSignatureNeedsCorrection) {
                        bufferedImageSignature = offsetImage(bufferedImageSignature, configuration.getBackgroundColor(), configuration.getSignatureFontType().getLeft(), configuration.getSignatureFontType().getRight(), configuration.getSignatureFontType().getTop(), configuration.getSignatureFontType().getBottom());
                    }
                    if (finalWidth != null || finalHeight != null) {
                        bufferedImageSignature = scaleImage(bufferedImageSignature, finalWidth, finalHeight);
                    }
                }
            }


            //----------------------------------------------------------------------------------------------------------
            //----------------------------------------------------------------------------------------------------------
            //----------------------------------------------------------------------------------------------------------

            if (bufferedImageSession != null) {
                bufferedImageList.add(bufferedImageSession);
            }
            if (bufferedImageSignature != null) {
                bufferedImageList.add(bufferedImageSignature);
            }
            if (bufferedImageTrace != null) {
                bufferedImageList.add(bufferedImageTrace);
            }
            if (bufferedImageDate != null) {
                bufferedImageList.add(bufferedImageDate);
            }
            signatureImage = joinImagesVertical(0, configuration.getBackgroundColor(), bufferedImageList.toArray(new BufferedImage[bufferedImageList.size()]));

            if (configuration.getSignatureFieldAppearance().hasBorder()) {
                Graphics2D graphics2D = signatureImage.createGraphics();
                Stroke stroke = new BasicStroke(2f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_ROUND);
                graphics2D.setColor(configuration.getBorderColor());
                graphics2D.setStroke(stroke);
                int circle = 5;
                int top = configuration.getSessionFontType().getTop() + circle;
                int left = 3;
                graphics2D.drawOval(configuration.getSessionFontType().getLeft() - 2 * circle - 1, top - 3, circle, circle);
                graphics2D.drawLine(configuration.getSessionFontType().getLeft() - 2 * circle, top, left, top);
                graphics2D.drawLine(left, top, left, bufferedImageSession.getHeight() + bufferedImageSignature.getHeight() - circle);
                graphics2D.drawLine(left, bufferedImageSession.getHeight() + bufferedImageSignature.getHeight() - circle, signatureImage.getWidth() - circle, bufferedImageSession.getHeight() + bufferedImageSignature.getHeight() - circle);
                graphics2D.drawOval(signatureImage.getWidth() - circle, bufferedImageSession.getHeight() + bufferedImageSignature.getHeight() - circle - 3, circle, circle);
                graphics2D.dispose();
            }

            //signatureImage = scaleImage(signatureImage, configuration.getNewWidth(), configuration.getNewHeight());

            try {
                ImageIO.write(signatureImage, configuration.getImageType().name(), signatureImageStream);
            } catch (IOException e) {
                throw new PdfUtilException("Unable to write signature file into an image stream", e);
            }
            SignatureConfiguration finalConfiguration = configuration;
            resource = new ByteArrayResource(signatureImageStream.toByteArray()) {
                @Override
                public String getFilename() {
                    return signatureCartridgeRequest.getSession() + "." + finalConfiguration.getImageType().name();
                }
            };

            String contentType = URLConnection.guessContentTypeFromName(resource.getFilename());
            signatureCartridgeResponse = SignatureCartridgeResponse.builder().resource(resource).contentType(contentType).build();

            return signatureCartridgeResponse;
        } finally {
        }
    }


    public BufferedImage toImage(
            List<String> lines, Font font, Color backgroundColor, Color foregroundColor,
            int left, int right, int top, int bottom,
            Integer maxWidth, Integer maxHeight,
            Integer minWidth, Integer minHeight
    ) {
        BufferedImage helperImage = new BufferedImage(1, 1, IMAGE_TYPE);
        Graphics2D graphics2D = helperImage.createGraphics();
        graphics2D.setFont(font);
        FontMetrics fontMetrics = graphics2D.getFontMetrics();
        int width = 0;
        for (String line : lines) {
            int lineWidth = fontMetrics.stringWidth(line + " ") + left + right;
            if (lineWidth > width) {
                width = lineWidth;
            }
        }
        int height = (fontMetrics.getHeight() + top + bottom) * lines.size();
        graphics2D.dispose();

        int newWidth = minWidth != null ? Math.max(minWidth, width) : width;
        int newHeight = minHeight != null ? Math.max(minHeight, height) : height;
        newWidth = maxWidth != null ? Math.max(maxWidth, width) : width;
        newHeight = maxHeight != null ? Math.max(maxHeight, height) : height;


        BufferedImage finalImg = new BufferedImage(newWidth, newHeight, IMAGE_TYPE);
        graphics2D = finalImg.createGraphics();
        if (backgroundColor != null) {
            graphics2D.setColor(backgroundColor);
            graphics2D.fillRect(0, 0, newWidth, newHeight);
        }
        graphics2D.setRenderingHint(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
        graphics2D.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics2D.setRenderingHint(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
        graphics2D.setRenderingHint(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
        graphics2D.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics2D.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics2D.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        graphics2D.setFont(font);
        fontMetrics = graphics2D.getFontMetrics();
        graphics2D.setColor(foregroundColor);
        int y = fontMetrics.getAscent();
        for (String line : lines) {
            y += top;
            graphics2D.drawString(line, left, y);
            y += (fontMetrics.getHeight() + bottom);
        }
        graphics2D.dispose();
        int scaledWidth = maxWidth != null ? maxWidth : newWidth;
        int scaledHeight = maxHeight != null ? maxHeight : newHeight;
        finalImg = scaleImage(finalImg, scaledWidth, scaledHeight);

        return finalImg;
    }

    public BufferedImage offsetImage(BufferedImage bufferedImage, Color backgroundColor, Integer left, Integer right, Integer top, Integer bottom) {
        int width = left + bufferedImage.getWidth() + right;
        int height = top + bufferedImage.getHeight() + bottom;
        BufferedImage finalImage = new BufferedImage(width, height, IMAGE_TYPE);
        Graphics2D graphics2D = finalImage.createGraphics();
        if (backgroundColor != null) {
            graphics2D.setColor(backgroundColor);
            graphics2D.fillRect(0, 0, width, height);
        }
        graphics2D.drawImage(bufferedImage, null, left, top);
        graphics2D.dispose();
        return finalImage;
    }

    public BufferedImage toImage(
            String multiline, Font font, Color backgroundColor, Color foregroundColor,
            int left, int right, int top, int bottom,
            Integer maxWidth, Integer maxHeight,
            Integer minWidth, Integer minHeight
    ) {
        String[] lines = multiline.split("\\r?\\n");
        return toImage(Arrays.asList(lines), font, backgroundColor, foregroundColor, left, right, top, bottom, maxWidth, maxHeight, minWidth, minHeight);
    }

    public BufferedImage joinImagesVertical(int offset, Color backgroundColor, BufferedImage... bufferedImages) {
        int width = 0;
        int height = 0;
        for (BufferedImage bufferedImage : bufferedImages) {
            if (bufferedImage.getWidth() > width) {
                width = bufferedImage.getWidth();
            }
            height = height + bufferedImage.getHeight();
        }
        height = height + offset * (bufferedImages.length - 1);
        BufferedImage finalImage = new BufferedImage(width, height, IMAGE_TYPE);
        Graphics2D graphics2D = finalImage.createGraphics();
        if (backgroundColor != null) {
            graphics2D.setColor(backgroundColor);
            graphics2D.fillRect(0, 0, width, height);
        }
        int y = 0;
        for (BufferedImage bufferedImage : bufferedImages) {
            graphics2D.drawImage(bufferedImage, null, 0, y);
            y = y + bufferedImage.getHeight() + offset;

        }
        graphics2D.dispose();
        return finalImage;
    }

    public static BufferedImage scaleImage(BufferedImage image, Integer width, Integer height) {
        int newHeight = height != null ? height : Integer.MAX_VALUE;
        int newWidth = width != null ? width : Integer.MAX_VALUE;
        if (image.getHeight() == newHeight && image.getWidth() == newWidth) {
            return image;
        }

        // Make sure the aspect ratio is maintained, so the image is not distorted
        double thumbRatio = (double) newWidth / (double) newHeight;
        int imageWidth = image.getWidth(null);
        int imageHeight = image.getHeight(null);
        double aspectRatio = (double) imageWidth / (double) imageHeight;

        if (thumbRatio < aspectRatio) {
            newHeight = (int) (newWidth / aspectRatio);
        } else {
            newWidth = (int) (newHeight * aspectRatio);
        }

        // Draw the scaled image
        BufferedImage newImage = new BufferedImage(newWidth, newHeight, IMAGE_TYPE);
        Graphics2D graphics2D = newImage.createGraphics();
        graphics2D.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics2D.drawImage(image, 0, 0, newWidth, newHeight, null);

        return newImage;
    }

    public Font createFont(FontType fontType) throws IOException, FontFormatException {
        if (fontType == null) {
            return null;
        }
        return getFont(fontType.getName(), fontType.getStyle(), fontType.getSize());
    }

    public Font getFont(String name, Integer style, Float size) throws IOException, FontFormatException {
        String key = name + "-" + String.valueOf(style) + String.valueOf(size);
        if (fontMap.containsKey(key)) {
            return fontMap.get(key);
        }
        String fontFilePathName = "/dss/fonts/" + name + (name.toLowerCase().endsWith(".ttf") ? "" : ".ttf");
        InputStream is = PdfUtilServiceImpl.class.getResourceAsStream(fontFilePathName);
        Font fontDefault = Font.createFont(Font.TRUETYPE_FONT, is);

        Font fontSpecific = fontDefault;
        if (size != null && style != null) {
            fontSpecific = fontDefault.deriveFont(style, size);
        }
        if (size != null && style == null) {
            fontSpecific = fontDefault.deriveFont(size);
        }
        if (size == null && style != null) {
            fontSpecific = fontDefault.deriveFont(style);
        }
        if (fontSpecific != null) {
            fontMap.put(key, fontSpecific);
        }
        return fontSpecific;
    }


    public Color createColor(String color) {
        if (color == null) {
            return null;
        }
        return ColorFactory.valueOf(color);
    }

    public SignatureConfiguration getSignatureConfiguration(SignatureConfigurationRequest signatureConfigurationRequest) throws IOException, FontFormatException {
        if (signatureConfigurationMap.containsKey(signatureConfigurationRequest)) {
            return signatureConfigurationMap.get(signatureConfigurationRequest);
        }
        SignatureConfiguration signatureConfiguration = SignatureConfiguration.builder()
                .mode(signatureConfigurationRequest.getMode())
                .signatureFieldAppearance(signatureConfigurationRequest.getSignatureFieldAppearance())
                .backgroundColor(createColor(signatureConfigurationRequest.getBackgroundColor()))
                .foregroundColor(createColor(signatureConfigurationRequest.getForegroundColor()))
                .borderColor(createColor(signatureConfigurationRequest.getBorderColor()))
                .sessionFont(createFont(signatureConfigurationRequest.getSessionFontType()))
                .sessionFontType(signatureConfigurationRequest.getSessionFontType())
                .sessionColor(createColor(signatureConfigurationRequest.getSessionColor()))
                .sessionLabel(signatureConfigurationRequest.getSessionLabel())
                .signatureFont(createFont(signatureConfigurationRequest.getSignatureFontType()))
                .signatureFontType(signatureConfigurationRequest.getSignatureFontType())
                .signatureColor(createColor(signatureConfigurationRequest.getSignatureColor()))
                .traceFont(createFont(signatureConfigurationRequest.getTraceFontType()))
                .traceFontType(signatureConfigurationRequest.getTraceFontType())
                .traceColor(createColor(signatureConfigurationRequest.getTraceColor()))
                .dateFont(createFont(signatureConfigurationRequest.getDateFontType()))
                .dateFontType(signatureConfigurationRequest.getDateFontType())
                .dateLabel(signatureConfigurationRequest.getDateLabel())
                .dateColor(createColor(signatureConfigurationRequest.getDateColor()))
                .newWidth(signatureConfigurationRequest.getNewWidth())
                .newHeight(signatureConfigurationRequest.getNewHeight())
                .imageType(signatureConfigurationRequest.getImageType())
                .build();
        signatureConfigurationMap.put(signatureConfigurationRequest, signatureConfiguration);
        return signatureConfiguration;
    }

    @Override
    public SignatureCartridgeRenderer createRenderer(SignatureCartridgeRequest signatureCartridgeRequest) {
        return new SignatureCartridgeRendererImpl(this, signatureCartridgeRequest);
    }


    //    protected OutputStream createOutputStream(boolean isFileBased) throws IOException {
//        if (isFileBased) {
//            final File tempFile = File.createTempFile("", ".tmp");
//            tempFile.deleteOnExit();
//            return new FileOutputStream(tempFile) {
//                @Override
//                public void close() throws IOException {
//                    try {
//                        super.close();
//                    } finally {
//                        FileUtils.deleteQuietly(tempFile);
//                    }
//                }
//            };
//        }
//        return new ByteArrayOutputStream();
//    }
//
//    public PDDocument replaceText(PDDocument document, String searchString, String replacement) throws IOException {
//        final PDPage page = document.getPage(0);
//        PDFStreamParser parser = new PDFStreamParser(page);
//        parser.parse();
//        List tokens = parser.getTokens();
//        for (int j = 0; j < tokens.size(); j++) {
//            Object next = tokens.get(j);
//            if (next instanceof Operator) {
//                Operator op = (Operator) next;
//                //Tj and TJ are the two operators that display strings in a PDF
//                if (op.getName().equals("Tj")) {
//                    // Tj takes one operator and that is the string to display so lets update that operator
//                    COSString previous = (COSString) tokens.get(j - 1);
//                    String string = previous.getString();
//                    //string = string.replaceFirst(searchString, replacement);
//                    string = StringUtils.replaceOnce(string, searchString, replacement);
//                    previous.setValue(string.getBytes());
//                    System.out.println(previous.getString());
//                } else if (op.getName().equals("TJ")) {
//                    COSArray previous = (COSArray) tokens.get(j - 1);
//                    for (int k = 0; k < previous.size(); k++) {
//                        Object arrElement = previous.getObject(k);
//                        if (arrElement instanceof COSString) {
//                            COSString cosString = (COSString) arrElement;
//                            String string = cosString.getString();
//                            string = StringUtils.replaceOnce(string, searchString, replacement);
//                            cosString.setValue(string.getBytes());
//                        }
//                    }
//                }
//            }
//        }
//        // now that the tokens are updated we will replace the page content stream.
//        PDStream updatedStream = new PDStream(document);
//        OutputStream out = updatedStream.createOutputStream();
//        ContentStreamWriter tokenWriter = new ContentStreamWriter(out);
//        tokenWriter.writeTokens(tokens);
//        page.setContents(updatedStream);
//        out.close();
//        return document;
//    }
//
//    public PDDocument replaceText(File document, String searchString, String replacement) throws IOException {
//        PdfReader reader = new PdfReader(FileUtils.openInputStream(document));
//        PdfDictionary dict = reader.getPageN(1);
//        PdfObject object = dict.getDirectObject(PdfName.CONTENTS);
//        PdfArray refs = null;
//        if (dict.get(PdfName.CONTENTS).isArray()) {
//            refs = dict.getAsArray(PdfName.CONTENTS);
//        } else if (dict.get(PdfName.CONTENTS).isIndirect()) {
//            refs = new PdfArray(dict.get(PdfName.CONTENTS));
//        }
//        for (int i = 0; i < refs.getArrayList().size(); i++) {
//            PRStream stream = (PRStream) refs.getDirectObject(i);
//            byte[] data = PdfReader.getStreamBytes(stream);
//            stream.setData(new String(data).replace(searchString, replacement).getBytes());
//        }
//        PdfStamper stamper = new PdfStamper(reader, new FileOutputStream("result123.pdf"));
//        stamper.close();
//        reader.close();
//        return null;
//    }
}
