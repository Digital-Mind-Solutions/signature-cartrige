package org.digitalmind.signaturecartrige.service.impl;

import com.lowagie.text.PageSize;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.beryx.awt.color.ColorFactory;
import org.digitalmind.signaturecartrige.dto.*;
import org.digitalmind.signaturecartrige.enumeration.SignatureFieldAppearance;
import org.digitalmind.signaturecartrige.exception.PdfUtilException;
import org.digitalmind.signaturecartrige.exception.PdfUtilRuntimeException;
import org.digitalmind.signaturecartrige.sam.SignatureCartridgeRenderer;
import org.digitalmind.signaturecartrige.sam.impl.SignatureCartridgeRendererImpl;
import org.digitalmind.signaturecartrige.service.PdfUtilService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static org.digitalmind.signaturecartrige.config.SignatureCartrigeModuleConfig.ENABLED;

@Service
@ConditionalOnProperty(name = ENABLED, havingValue = "true")
@Slf4j
public class PdfUtilServiceImpl implements PdfUtilService {

    private static final int IMAGE_TYPE = BufferedImage.TYPE_INT_ARGB;
    private Map<SignatureConfigurationRequest, SignatureConfiguration> signatureConfigurationMap = new ConcurrentHashMap<>();
    private Map<String, Font> fontMap = new ConcurrentHashMap<>();

    @NoArgsConstructor
    @AllArgsConstructor
    @Data
    public static class Tuple {
        private int a;
        private int b;
    }

    @Override
    public InspectContentResponse inspect(InspectContentRequest request) throws IOException {
        Assert.notNull(request, this.getClass().getSimpleName() + ".validateSignatureFields: ValidateSignatureRequest must not be null");
        Assert.notNull(request.getInputStream(), this.getClass().getSimpleName() + ".validateSignatureFields: Pdf stream must not be null");
        InspectContentResponse.InspectContentResponseBuilder builder = InspectContentResponse.builder();

        List<String> requestFieldNameOrPatternList = request.getSignatureFields() != null ? request.getSignatureFields() : Collections.emptyList();
        try (PdfReader reader = new PdfReader(request.getInputStream())) {
            AcroFields acroFields = reader.getAcroFields();
            List<String> signatureFieldNames = acroFields.getFieldNamesWithBlankSignatures();

            builder.signatureOk(true);
            signatureFieldNames.forEach(signatureFieldName -> {
                float[] pos = acroFields.getFieldPositions(signatureFieldName);
                builder.signatureFieldDetail(signatureFieldName, pos);
                if (pos.length > 5) {
                    builder.signatureRepeatedField(signatureFieldName);
                    builder.signatureOk(false);
                }
            });

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
                        String fieldCurrentValue = acroFields.getField(fieldName);
                        String fieldNextValue = entry.getValue();
                        acroFields.setField(fieldName, fieldNextValue);

                        //if (fieldValue != null) {
                        //    acroFields.setField(entry.getKey(), entry.getValue());
                        //    //acroFields.setFieldProperty(entry.getKey(), "setfflags", PdfFormField.FF_READ_ONLY, null);
                        //}
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
            AcroFields acroFields = stamper.getAcroFields();
            Set<String> flattenFieldNames = new HashSet<>();
            Set<String> acroFieldNames = acroFields.getAllFields().keySet();
            Set<String> requestFlattenFieldNames = null;

            if (!ObjectUtils.isEmpty(request.getFlattenFields()) && request.getFlattenFields().size() == 1 && "*".equalsIgnoreCase(request.getFlattenFields().get(0))) {
                requestFlattenFieldNames = new HashSet<>(acroFieldNames);
            } else {
                requestFlattenFieldNames = match(request.getFlattenFields(), acroFieldNames);
            }
            Set<String> requestNonFlattenFieldNames = match(request.getNonFlattenFields(), acroFieldNames);

            flattenFieldNames.addAll(requestFlattenFieldNames);

            flattenFieldNames.removeAll(requestNonFlattenFieldNames);

            if (Boolean.TRUE.equals(request.getFlattenSignatureFields())) {
                for (String fieldName : acroFieldNames) {
                    if (AcroFields.FIELD_TYPE_SIGNATURE == acroFields.getFieldType(fieldName)) {
                        flattenFieldNames.add(fieldName);
                    }
                }
            }

            if (Boolean.FALSE.equals(request.getFlattenSignatureFields())) {
                for (String fieldName : acroFieldNames) {
                    if (AcroFields.FIELD_TYPE_SIGNATURE == acroFields.getFieldType(fieldName)) {
                        flattenFieldNames.remove(fieldName);
                    }
                }
            }


            if (flattenFieldNames.size() != acroFieldNames.size()) {
                for (String fieldName : flattenFieldNames) {
                    stamper.partialFormFlattening(fieldName);
                }

            }

            stamper.setFormFlattening(true);
            stamper.setFreeTextFlattening(true);
        } finally {
            stamper.close();
        }
        return response;
    }

    @Override
    public AddSignatureContentResponse addSignatureFields(AddSignatureContentRequest request) throws IOException {
        Assert.notNull(request.getInputStream(), this.getClass().getSimpleName() + ".addSignatures: Pdf stream must not be null");
        AddSignatureContentResponse response = new AddSignatureContentResponse();
        PdfStamper stamper = null;
        try (PdfReader reader = new PdfReader(request.getInputStream())) {
            stamper = new PdfStamper(reader, request.getOutputStream(), '\0', false);
            // close pdf stamper
            if (request.getSignatureFields() != null && request.getSignatureFields().size() > 0) {
                for (Map.Entry<String, PdfFieldPosition> entry : request.getSignatureFields().entrySet()) {
                    String pdfFieldName = entry.getKey();
                    PdfFieldPosition pdfFieldPosition = entry.getValue();
                    PdfFormField signatureField = PdfFormField.createSignature(stamper.getWriter());
                    signatureField.setWidget(new Rectangle(pdfFieldPosition.getLeft(), pdfFieldPosition.getTop(), pdfFieldPosition.getRight(), pdfFieldPosition.getBottom()), null);
                    signatureField.setFlags(PdfAnnotation.FLAGS_PRINT);
                    signatureField.put(PdfName.DA, new PdfString("/Helv 0 Tf 0 g"));
                    signatureField.setFieldName(pdfFieldName);
                    signatureField.setPage((int) pdfFieldPosition.getPage());
                    stamper.addAnnotation(signatureField, 1);
                }
            }
            stamper.setFormFlattening(true);
        } finally {
            stamper.close();
        }
        return response;
    }


    public Set<String> match(Collection<String> fieldNameOrPatternCollection, Collection<String> fieldNameCollection) {
        final Set<String> result = new HashSet<>();
        if (ObjectUtils.isEmpty(fieldNameCollection)) {
            //do nothing
        } else {
            fieldNameOrPatternCollection.stream().forEach(
                    fieldNameOrPattern -> {
                        result.addAll(match(fieldNameOrPattern, fieldNameCollection));
                    }
            );
        }
        return result;
    }


    public Set<String> match(String fieldNameOrPattern, Collection<String> fieldNames) {
        Set<String> result = Collections.emptySet();
        if (ObjectUtils.isEmpty(fieldNameOrPattern) || ObjectUtils.isEmpty(fieldNames)) {
            //do nothing
        } else {
            if (fieldNameOrPattern.contains("*") || fieldNameOrPattern.contains("?")) {
                result = fieldNames.stream().filter(fieldName -> match(fieldNameOrPattern, fieldName)).collect(Collectors.toSet());
            } else {
                result = fieldNames.stream().filter(fieldName -> fieldNameOrPattern.equals(fieldName)).collect(Collectors.toSet());
            }
        }
        return result;
    }

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
                (
                        fieldNameOrPattern.length() != 0 && fieldName.length() != 0 &&
                                fieldNameOrPattern.charAt(0) == fieldName.charAt(0)
                ))
            return match(
                    fieldNameOrPattern.substring(1),
                    fieldName.substring(1)
            );

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
        try {
            ByteArrayOutputStream signatureImageStream = new ByteArrayOutputStream();
            ByteArrayResource resource;
            BufferedImage signatureImage = null;
            SignatureConfiguration configuration = null;
            try {
                configuration = getSignatureConfiguration(signatureCartridgeRequest.getConfiguration());
            } catch (IOException | FontFormatException e) {
                throw new PdfUtilException("Unable to load signature configuration", e);
            }
            SignatureFieldAppearance signatureFieldAppearance = configuration.getSignatureFieldAppearance();
            int cummulatedHeight = 0;
            signatureImage = createImage(configuration.getBackgroundColor(), configuration.getTransparentColor(), finalWidth, finalHeight);
            RenderingHints renderingHints = new RenderingHints(new HashMap<>());
            renderingHints.put(RenderingHints.KEY_ALPHA_INTERPOLATION, RenderingHints.VALUE_ALPHA_INTERPOLATION_QUALITY);
            renderingHints.put(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            renderingHints.put(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            renderingHints.put(RenderingHints.KEY_COLOR_RENDERING, RenderingHints.VALUE_COLOR_RENDER_QUALITY);
            renderingHints.put(RenderingHints.KEY_DITHERING, RenderingHints.VALUE_DITHER_ENABLE);
            renderingHints.put(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            renderingHints.put(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            renderingHints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            renderingHints.put(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            //----------------------------------------------------------------------------------------------------------
            TextRenderDetails sessionRenderDetails = null;
            if (signatureFieldAppearance.hasSession()) {
                sessionRenderDetails = calcRenderDetails(
                        configuration.getSessionLabel() + signatureCartridgeRequest.getSession(),
                        configuration.getSessionFontType(),
                        configuration.getBackgroundColor(),
                        configuration.getTransparentColor(),
                        configuration.getSessionColor() != null ? configuration.getSessionColor() : configuration.getForegroundColor(),
                        finalWidth, finalHeight
                );
                cummulatedHeight += sessionRenderDetails.getHeight();
            }

            //----------------------------------------------------------------------------------------------------------
            TextRenderDetails traceRenderDetails = null;
            if (signatureFieldAppearance.hasTrace()) {
                traceRenderDetails = calcRenderDetails(
                        signatureCartridgeRequest.getTrace(),
                        configuration.getTraceFontType(),
                        configuration.getBackgroundColor(),
                        configuration.getTransparentColor(),
                        configuration.getTraceColor() != null ? configuration.getTraceColor() : configuration.getForegroundColor(),
                        finalWidth, finalHeight
                );
                cummulatedHeight += traceRenderDetails.getHeight();
            }

            //----------------------------------------------------------------------------------------------------------
            TextRenderDetails dateRenderDetails = null;
            if (signatureFieldAppearance.hasDate()) {
                dateRenderDetails = calcRenderDetails(
                        configuration.getDateLabel() + signatureCartridgeRequest.getDate(),
                        configuration.getDateFontType(),
                        configuration.getBackgroundColor(),
                        configuration.getTransparentColor(),
                        configuration.getDateColor() != null ? configuration.getDateColor() : configuration.getForegroundColor(),
                        finalWidth, finalHeight
                );
                cummulatedHeight += dateRenderDetails.getHeight();
            }

            //----------------------------------------------------------------------------------------------------------
            BufferedImage bufferedImageSignature = null;
            boolean bufferedImageSignatureNeedsCorrection = true;
            Integer signatureHeight = 0;
            if (signatureFieldAppearance.hasSignature()) {
                //this field can be text or image
                signatureHeight = finalHeight != null && (finalHeight - cummulatedHeight) >= 0
                        ? finalHeight - cummulatedHeight
                        : finalHeight;
                if (signatureCartridgeRequest.getSignature() instanceof String) {
                    TextRenderDetails signatureRenderDetails = null;
                    signatureRenderDetails = calcRenderDetails(
                            (String) signatureCartridgeRequest.getSignature(),
                            configuration.getSignatureFontType(),
                            configuration.getBackgroundColor(),
                            configuration.getTransparentColor(),
                            configuration.getSignatureColor() != null ? configuration.getSignatureColor() : configuration.getForegroundColor(),
                            finalWidth, signatureHeight
                    );
                    int dx = finalWidth > signatureRenderDetails.getWidth() ? (finalWidth - signatureRenderDetails.getWidth()) / 2 : 0;
                    int dy = signatureHeight > signatureRenderDetails.getHeight() ? (signatureHeight - signatureRenderDetails.getHeight()) / 2 : 0;

                    bufferedImageSignature = createImage(configuration.getBackgroundColor(), configuration.getTransparentColor(), finalWidth, finalHeight);
                    writeImage(bufferedImageSignature, (String) signatureCartridgeRequest.getSignature(), dx, dy, signatureRenderDetails, renderingHints);

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
                        bufferedImageSignature = getCroppedImage(bufferedImageSignature, configuration.getBackgroundColor(), 5);
                        bufferedImageSignature = scaleImage(bufferedImageSignature, finalWidth, signatureHeight);

                        int dx = finalWidth > bufferedImageSignature.getWidth() ? (finalWidth - bufferedImageSignature.getWidth()) / 2 : 0;
                        int dy = signatureHeight > bufferedImageSignature.getHeight() ? (signatureHeight - bufferedImageSignature.getHeight()) / 2 : 0;

                        BufferedImage baseSignature = createImage(configuration.getBackgroundColor(), configuration.getTransparentColor(), finalWidth, signatureHeight);
                        overlayImage(baseSignature, bufferedImageSignature, dx, dy);
                        bufferedImageSignature = baseSignature;

                        if (finalWidth != null || finalHeight != null) {
                            bufferedImageSignature = scaleImage(bufferedImageSignature, finalWidth, signatureHeight);
                        }

                    }

                }
            }


            //----------------------------------------------------------------------------------------------------------
            //write cartridge
            //----------------------------------------------------------------------------------------------------------

            if (signatureFieldAppearance.hasSession()) {
                writeImage(signatureImage,
                           configuration.getSessionLabel() + signatureCartridgeRequest.getSession(),
                           0, 0,
                           sessionRenderDetails,
                           renderingHints
                );
            }
            if (signatureFieldAppearance.hasTrace()) {
                writeImage(signatureImage,
                           signatureCartridgeRequest.getTrace(),
                           0, 0 + (sessionRenderDetails != null ? sessionRenderDetails.getHeight() : 0) + signatureHeight,
                           traceRenderDetails,
                           renderingHints
                );
            }
            if (signatureFieldAppearance.hasDate()) {
                writeImage(signatureImage,
                           configuration.getDateLabel() + signatureCartridgeRequest.getDate(),
                           0, 0 + (sessionRenderDetails != null ? sessionRenderDetails.getHeight() : 0) + signatureHeight + (traceRenderDetails != null ? traceRenderDetails.getHeight() : 0),
                           dateRenderDetails,
                           renderingHints
                );
            }

            if (configuration.getSignatureFieldAppearance().hasBorder()) {
                Graphics2D graphics2D = signatureImage.createGraphics();
                Stroke stroke = new BasicStroke(2f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_ROUND);
                graphics2D.setColor(configuration.getBorderColor());
                graphics2D.setStroke(stroke);
                int circle = 7;
                int top = sessionRenderDetails.getHeight() / 2;
                int left = 10;
                graphics2D.drawOval(configuration.getSessionFontType().getLeft() - 2 * circle - 1, top - circle / 2, circle, circle);
                graphics2D.drawLine(configuration.getSessionFontType().getLeft() - 2 * circle, top, left, top);
                graphics2D.drawLine(left, top, left, sessionRenderDetails.getHeight() + signatureHeight - circle);
                graphics2D.drawLine(left, sessionRenderDetails.getHeight() + signatureHeight - circle, finalWidth - circle, sessionRenderDetails.getHeight() + signatureHeight - circle);
                graphics2D.drawOval(finalWidth - circle, sessionRenderDetails.getHeight() + signatureHeight - circle - circle / 2, circle, circle);
                graphics2D.dispose();
            }

            overlayImage(signatureImage, bufferedImageSignature, 0, 0 + (sessionRenderDetails != null ? sessionRenderDetails.getHeight() : 0));

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

        } finally {
        }
        return signatureCartridgeResponse;
    }

    public SignatureCartridgeResponse createSignatureImageOld(SignatureCartridgeRequest signatureCartridgeRequest, Integer width, Integer height) throws PdfUtilException {
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
                        configuration.getSessionFontType(),
                        configuration.getBackgroundColor(),
                        configuration.getTransparentColor(),
                        configuration.getSessionColor() != null ? configuration.getSessionColor() : configuration.getForegroundColor(),
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
                        configuration.getTraceFontType(),
                        configuration.getBackgroundColor(),
                        configuration.getTransparentColor(),
                        configuration.getTraceColor() != null ? configuration.getTraceColor() : configuration.getForegroundColor(),
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
                        configuration.getDateFontType(),
                        configuration.getBackgroundColor(),
                        configuration.getTransparentColor(),
                        configuration.getDateColor() != null ? configuration.getDateColor() : configuration.getForegroundColor(),
                        configuration.getDateFontType().getLeft(), configuration.getDateFontType().getRight(), configuration.getDateFontType().getTop(), configuration.getDateFontType().getBottom(),
                        finalWidth, null,
                        null, null
                );
                cummulatedHeight += bufferedImageDate.getHeight();
            }

            //--------------------------------------------------------------------------------------------------------------
            BufferedImage bufferedImageSignature = null;
            boolean bufferedImageSignatureNeedsCorrection = true;
            Integer signatureHeight = 0;
            if (signatureFieldAppearance.hasSignature()) {
                //this field can be text or image
                signatureHeight = finalHeight != null && (finalHeight - cummulatedHeight) >= 0
                        ? finalHeight - cummulatedHeight
                        : finalHeight;
                if (signatureCartridgeRequest.getSignature() instanceof String) {
                    bufferedImageSignature = toImage(
                            (String) signatureCartridgeRequest.getSignature(),
                            configuration.getSignatureFontType(),
                            configuration.getBackgroundColor(),
                            configuration.getTransparentColor(),
                            configuration.getSignatureColor() != null ? configuration.getSignatureColor() : configuration.getForegroundColor(),
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
                        bufferedImageSignature = offsetImage(bufferedImageSignature, configuration.getBackgroundColor(), configuration.getTransparentColor(), configuration.getSignatureFontType().getLeft(), configuration.getSignatureFontType().getRight(), configuration.getSignatureFontType().getTop(), configuration.getSignatureFontType().getBottom());
                    }

                    //cred ca trebuie sa fie signatureHeight
                    if (finalWidth != null || finalHeight != null) {
                        bufferedImageSignature = scaleImage(bufferedImageSignature, finalWidth, finalHeight);
                    }
                }
            }


            //----------------------------------------------------------------------------------------------------------
            //----------------------------------------------------------------------------------------------------------
            //----------------------------------------------------------------------------------------------------------

            int signatureCartridgeWidth = 0;
            int signatureCartridgeHeight = 0;
            ArrayList<Integer> layers = new ArrayList<Integer>();
            if (bufferedImageSession != null) {
                bufferedImageList.add(bufferedImageSession);
                layers.add(1);
                if (signatureCartridgeWidth < bufferedImageSession.getWidth()) {
                    signatureCartridgeWidth = bufferedImageSession.getWidth();
                }
                signatureCartridgeHeight += bufferedImageSession.getHeight();
            }
            if (bufferedImageSignature != null) {
                layers.add(4);
                bufferedImageList.add(bufferedImageSignature);
                if (signatureCartridgeWidth < bufferedImageSignature.getWidth()) {
                    signatureCartridgeWidth = bufferedImageSignature.getWidth();
                }
                signatureCartridgeHeight += signatureHeight;
            }
            if (bufferedImageTrace != null) {
                layers.add(3);
                bufferedImageList.add(bufferedImageTrace);
                if (signatureCartridgeWidth < bufferedImageTrace.getWidth()) {
                    signatureCartridgeWidth = bufferedImageTrace.getWidth();
                }
                signatureCartridgeHeight += bufferedImageTrace.getHeight();
            }
            if (bufferedImageDate != null) {
                layers.add(2);
                bufferedImageList.add(bufferedImageDate);
                if (signatureCartridgeWidth < bufferedImageDate.getWidth()) {
                    signatureCartridgeWidth = bufferedImageDate.getWidth();
                }
                signatureCartridgeHeight += bufferedImageDate.getHeight();
            }

            //--------------------------------------------------------------------------------------------------------------
            BufferedImage bufferedImageBorder = null;
            if (configuration.getSignatureFieldAppearance().hasBorder()) {
                layers.add(0);
                bufferedImageBorder = new BufferedImage(signatureCartridgeWidth, signatureCartridgeHeight, IMAGE_TYPE);
                Graphics2D graphics2D = bufferedImageBorder.createGraphics();
                Stroke stroke = new BasicStroke(2f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_ROUND);
                graphics2D.setColor(configuration.getBorderColor());
                graphics2D.setStroke(stroke);
                int circle = 5;
                int top = configuration.getSessionFontType().getTop() + circle;
                int left = 3;
                graphics2D.drawOval(configuration.getSessionFontType().getLeft() - 2 * circle - 1, top - 3, circle, circle);
                graphics2D.drawLine(configuration.getSessionFontType().getLeft() - 2 * circle, top, left, top);
                graphics2D.drawLine(left, top, left, bufferedImageSession.getHeight() + signatureHeight - circle);
                graphics2D.drawLine(left, bufferedImageSession.getHeight() + signatureHeight - circle, signatureCartridgeWidth - circle, bufferedImageSession.getHeight() + signatureHeight - circle);
                graphics2D.drawOval(signatureCartridgeWidth - circle, bufferedImageSession.getHeight() + signatureHeight - circle - 3, circle, circle);
                graphics2D.dispose();
                if (configuration.getTransparentColor() != null) {
                    bufferedImageBorder = makeTransparent(bufferedImageBorder, configuration.getTransparentColor());
                }
                bufferedImageList.add(bufferedImageBorder);
            }


            signatureImage = joinImagesVerticalLayers(
                    0,
                    configuration.getBackgroundColor(), configuration.getTransparentColor(),
                    bufferedImageList.toArray(new BufferedImage[bufferedImageList.size()]),
                    layers.toArray(new Integer[layers.size()])
            );


            //signatureImage = scaleImage(signatureImage, configuration.getNewWidth(), configuration.getNewHeight());

            if (configuration.getTransparentColor() != null) {
                signatureImage = makeTransparent(signatureImage, configuration.getTransparentColor());
            }

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


    //------------------------------------------------------------------------------------------------------------------
    //------------------------------------------------------------------------------------------------------------------
    //------------------------------------------------------------------------------------------------------------------
    //------------------------------------------------------------------------------------------------------------------

    public BufferedImage createImage(Color backgroundColor, Color transparentColor, int width, int height) {
        BufferedImage bufferedImage = new BufferedImage(width, height, IMAGE_TYPE);
        Graphics2D graphics2D = bufferedImage.createGraphics();
        if (backgroundColor != null) {
            if (transparentColor == null || !transparentColor.equals(backgroundColor)) {
                graphics2D.setColor(backgroundColor);
                graphics2D.fillRect(0, 0, width, height);
            }
        }
        if (transparentColor != null) {
            bufferedImage = makeTransparent(bufferedImage, transparentColor);
        }
        return bufferedImage;
    }

    public BufferedImage makeTransparent(final BufferedImage image, final Color transparentColor) {
        final ImageFilter filter = new RGBImageFilter() {
            // the color we are looking for (white)... Alpha bits are set to opaque
            public int markerRGB = transparentColor.getRGB() | 0xFFFFFFFF;

            public final int filterRGB(final int x, final int y, final int rgb) {
                if ((rgb | 0xFF000000) == markerRGB) {
                    // Mark the alpha bits as zero - transparent
                    return 0x00FFFFFF & rgb;
                } else {
                    // nothing to do
                    return rgb;
                }
            }
        };
        final ImageProducer ip = new FilteredImageSource(image.getSource(), filter);
        return toBufferedImage(Toolkit.getDefaultToolkit().createImage(ip));
    }

    public TextRenderDetails calcRenderDetails(
            String line, FontType fontType,
            Color backgroundColor, Color transparentColor, Color foregroundColor,
            Integer maxWidth, Integer maxHeight
    ) {
        return calcRenderDetails(Arrays.asList(line), fontType,
                                 backgroundColor, transparentColor, foregroundColor,
                                 maxWidth, maxHeight
        );
    }

    public TextRenderDetails calcRenderDetails(
            List<String> lines, FontType fontType,
            Color backgroundColor, Color transparentColor, Color foregroundColor,
            Integer maxWidth, Integer maxHeight
    ) {
        TextRenderDetails textRenderDetails = new TextRenderDetails();
        BufferedImage helperImage = new BufferedImage(1, 1, IMAGE_TYPE);
        Graphics2D graphics2D = helperImage.createGraphics();
        Font font;
        FontMetrics fontMetrics;
        float size = fontType.getSize();
        int width;
        int height;
        do {
            font = getFont(fontType.getName(), fontType.getStyle(), size);
            graphics2D.setFont(font);
            fontMetrics = graphics2D.getFontMetrics();
            width = 0;
            height = (fontMetrics.getHeight() + fontType.getTop() + fontType.getBottom()) * lines.size();
            for (String line : lines) {
                int lineWidth = fontMetrics.stringWidth(line + " ") + fontType.getLeft() + fontType.getRight();
                if (lineWidth > width) {
                    width = lineWidth;
                }
            }
            size--;
        } while (((maxWidth != null && width > maxWidth) || (maxHeight != null && height > maxHeight)) && size > 0);
        graphics2D.dispose();

        int newWidth = width;
        int newHeight = height;

        FontType fontTypeReturn = new FontType(fontType);
        fontTypeReturn.setSize(++size);
        textRenderDetails.setFontType(fontTypeReturn);
        textRenderDetails.setWidth(newWidth);
        textRenderDetails.setHeight(newHeight);
        textRenderDetails.setBackgroundColor(backgroundColor);
        textRenderDetails.setTransparentColor(transparentColor);
        textRenderDetails.setForegroundColor(foregroundColor);
        return textRenderDetails;
    }

    public void writeImage(BufferedImage bufferedImage, String line, int x, int y, TextRenderDetails textRenderDetails, RenderingHints renderingHints) {
        writeImage(bufferedImage, Arrays.asList(line), x, y, textRenderDetails, renderingHints);
    }

    public void writeImage(BufferedImage bufferedImage, List<String> lines, int x, int y, TextRenderDetails textRenderDetails, RenderingHints renderingHints) {
        Graphics2D graphics2D = bufferedImage.createGraphics();
        if (textRenderDetails.getBackgroundColor() != null) {
            if (textRenderDetails.getTransparentColor() == null || !textRenderDetails.getTransparentColor().equals(textRenderDetails.getBackgroundColor())) {
                graphics2D.setColor(textRenderDetails.getBackgroundColor());
                graphics2D.fillRect(x, y, textRenderDetails.getWidth() + x, textRenderDetails.getHeight() + y);
            }
        }
        if (renderingHints != null && renderingHints.size() > 0) {
            graphics2D.setRenderingHints(renderingHints);
        }
        Font font = createFont(textRenderDetails.getFontType());
        graphics2D.setFont(font);
        FontMetrics fontMetrics = graphics2D.getFontMetrics();
        graphics2D.setColor(textRenderDetails.getForegroundColor());
        int yPos = fontMetrics.getAscent() + y;
        for (String line : lines) {
            yPos += textRenderDetails.getFontType().getTop();
            graphics2D.drawString(line, textRenderDetails.getFontType().getLeft() + x, yPos);
            yPos += (fontMetrics.getHeight() + textRenderDetails.getFontType().getBottom());
        }
        graphics2D.dispose();
    }


    public BufferedImage toImage(
            List<String> lines, FontType fontType, Color backgroundColor, Color transparentColor, Color foregroundColor,
            int left, int right, int top, int bottom,
            Integer maxWidth, Integer maxHeight,
            Integer minWidth, Integer minHeight
    ) {
        BufferedImage helperImage = new BufferedImage(1, 1, IMAGE_TYPE);
        Graphics2D graphics2D = helperImage.createGraphics();
        Font font;
        FontMetrics fontMetrics;
        float size = fontType.getSize();
        int width;
        int height;
        do {
            font = getFont(fontType.getName(), fontType.getStyle(), size);
            graphics2D.setFont(font);
            fontMetrics = graphics2D.getFontMetrics();
            width = 0;
            height = (fontMetrics.getHeight() + top + bottom) * lines.size();
            for (String line : lines) {
                int lineWidth = fontMetrics.stringWidth(line + " ") + left + right;
                if (lineWidth > width) {
                    width = lineWidth;
                }
            }
            size--;
        } while (((maxWidth != null && width > maxWidth) || (maxHeight != null && height > maxHeight)) && size > 4);
        graphics2D.dispose();

        int newWidth = minWidth != null ? Math.max(minWidth, width) : width;
        int newHeight = minHeight != null ? Math.max(minHeight, height) : height;
        newWidth = maxWidth != null ? Math.max(maxWidth, width) : width;
        newHeight = maxHeight != null ? Math.max(maxHeight, height) : height;

        BufferedImage finalImg = new BufferedImage(newWidth, newHeight, IMAGE_TYPE);
        graphics2D = finalImg.createGraphics();
        if (backgroundColor != null) {
            if (transparentColor == null || !transparentColor.equals(backgroundColor)) {
                graphics2D.setColor(backgroundColor);
                graphics2D.fillRect(0, 0, newWidth, newHeight);
            }
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

        if (transparentColor != null) {
            finalImg = makeTransparent(finalImg, transparentColor);
        }
        return finalImg;
    }

    public BufferedImage toImage(
            List<String> lines, Font font, Color backgroundColor, Color transparentColor, Color foregroundColor,
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
            if (transparentColor == null || !transparentColor.equals(backgroundColor)) {
                graphics2D.setColor(backgroundColor);
                graphics2D.fillRect(0, 0, newWidth, newHeight);
            }
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
        if (transparentColor != null) {
            finalImg = makeTransparent(finalImg, transparentColor);
        }
        return finalImg;
    }

    public BufferedImage offsetImage(BufferedImage bufferedImage, Color backgroundColor, Color transparentColor, Integer left, Integer right, Integer top, Integer bottom) {
        int width = left + bufferedImage.getWidth() + right;
        int height = top + bufferedImage.getHeight() + bottom;
        BufferedImage finalImage = new BufferedImage(width, height, IMAGE_TYPE);
        Graphics2D graphics2D = finalImage.createGraphics();
        if (backgroundColor != null) {
            if (transparentColor == null || !transparentColor.equals(backgroundColor)) {
                graphics2D.setColor(backgroundColor);
                graphics2D.fillRect(0, 0, width, height);
            }
        }
        graphics2D.drawImage(bufferedImage, null, left, top);
        graphics2D.dispose();

        if (transparentColor != null) {
            finalImage = makeTransparent(finalImage, transparentColor);
        }
        return finalImage;
    }

    public void overlayImage(BufferedImage underImage, BufferedImage overImage, int x, int y) {
        Graphics2D graphics2D = underImage.createGraphics();
        graphics2D.drawImage(overImage, null, x, y);
        graphics2D.dispose();
    }

    public BufferedImage toImage(
            String multiline, Font font, Color backgroundColor, Color transparentColor, Color foregroundColor,
            int left, int right, int top, int bottom,
            Integer maxWidth, Integer maxHeight,
            Integer minWidth, Integer minHeight
    ) {
        String[] lines = multiline.split("\\r?\\n");
        return toImage(Arrays.asList(lines), font, backgroundColor, transparentColor, foregroundColor, left, right, top, bottom, maxWidth, maxHeight, minWidth, minHeight);
    }

    public BufferedImage toImage(
            String multiline, FontType fontType, Color backgroundColor, Color transparentColor, Color foregroundColor,
            int left, int right, int top, int bottom,
            Integer maxWidth, Integer maxHeight,
            Integer minWidth, Integer minHeight
    ) {
        String[] lines = multiline.split("\\r?\\n");
        return toImage(Arrays.asList(lines), fontType, backgroundColor, transparentColor, foregroundColor, left, right, top, bottom, maxWidth, maxHeight, minWidth, minHeight);
    }

    public BufferedImage joinImagesVerticalLayers(int offset, Color backgroundColor, Color transparentColor, BufferedImage[] bufferedImages, Integer[] layers) {
        int width = 0;
        int height = 0;
        int[] heights = new int[bufferedImages.length];
        List<Tuple> tupleList = new ArrayList<>();
        int i = 0;
        for (i = 0; i < bufferedImages.length; i++) {
            tupleList.add(new Tuple(layers[i], i));
            if (bufferedImages[i].getWidth() > width) {
                width = bufferedImages[i].getWidth();
            }
            height = height + ((i > 0) ? bufferedImages[i - 1].getHeight() : 0) + ((i > 0) ? offset : 0);
            heights[i] = height;
        }
        height = height + ((i > 0) ? bufferedImages[i - 1].getHeight() : 0) + ((i > 0) ? offset : 0);
        BufferedImage finalImage = new BufferedImage(width, height, IMAGE_TYPE);
        Graphics2D graphics2D = finalImage.createGraphics();
        if (backgroundColor != null) {
            if (transparentColor == null || !transparentColor.equals(backgroundColor)) {
                graphics2D.setColor(backgroundColor);
                graphics2D.fillRect(0, 0, width, height);
            }
        }
        tupleList.stream().sorted((tuple1, tuple2) -> Integer.compare(tuple1.getA(), tuple2.getA()))
                .forEach(tuple ->
                                 graphics2D.drawImage(bufferedImages[tuple.getB()], null, 0, heights[tuple.getB()])
                );
        graphics2D.dispose();
        return finalImage;
    }

    public BufferedImage joinImagesVertical(int offset, Color backgroundColor, Color transparentColor, BufferedImage... bufferedImages) {
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
            if (transparentColor == null || !transparentColor.equals(backgroundColor)) {
                graphics2D.setColor(backgroundColor);
                graphics2D.fillRect(0, 0, width, height);
            }
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
        if ((width == null && height == null) || (image.getHeight() == newHeight && image.getWidth() == newWidth)) {
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


    public static BufferedImage toBufferedImage(Image img) {
        if (img instanceof BufferedImage) {
            return (BufferedImage) img;
        }

        // Create a buffered image with transparency
        BufferedImage bimage = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);

        // Draw the image on to the buffered image
        Graphics2D bGr = bimage.createGraphics();
        bGr.drawImage(img, 0, 0, null);
        bGr.dispose();

        // Return the buffered image
        return bimage;
    }


    public BufferedImage getCroppedImage(BufferedImage bufferedImage, Color backgroundColor, double tolerance) {
        // Get our top-left pixel color as our "baseline" for cropping

        int baseColor = bufferedImage.getRGB(0, 0);
        if (backgroundColor != null) {
            baseColor = backgroundColor.getRGB();
        }

        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();

        int topY = Integer.MAX_VALUE, topX = Integer.MAX_VALUE;
        int bottomY = -1, bottomX = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (colorWithinTolerance(baseColor, bufferedImage.getRGB(x, y), tolerance)) {
                    if (x < topX) topX = x;
                    if (y < topY) topY = y;
                    if (x > bottomX) bottomX = x;
                    if (y > bottomY) bottomY = y;
                }
            }
        }

        BufferedImage finalImage = new BufferedImage((bottomX - topX + 1), (bottomY - topY + 1), IMAGE_TYPE);

        finalImage.getGraphics().drawImage(bufferedImage, 0, 0,
                                           finalImage.getWidth(), finalImage.getHeight(),
                                           topX, topY, bottomX, bottomY, null
        );

        return finalImage;
    }

    private boolean colorWithinTolerance(int a, int b, double tolerance) {
        int aAlpha = (int) ((a & 0xFF000000) >>> 24);   // Alpha level
        int aRed = (int) ((a & 0x00FF0000) >>> 16);   // Red level
        int aGreen = (int) ((a & 0x0000FF00) >>> 8);    // Green level
        int aBlue = (int) (a & 0x000000FF);            // Blue level

        int bAlpha = (int) ((b & 0xFF000000) >>> 24);   // Alpha level
        int bRed = (int) ((b & 0x00FF0000) >>> 16);   // Red level
        int bGreen = (int) ((b & 0x0000FF00) >>> 8);    // Green level
        int bBlue = (int) (b & 0x000000FF);            // Blue level

        double distance = Math.sqrt((aAlpha - bAlpha) * (aAlpha - bAlpha) +
                                            (aRed - bRed) * (aRed - bRed) +
                                            (aGreen - bGreen) * (aGreen - bGreen) +
                                            (aBlue - bBlue) * (aBlue - bBlue));

        // 510.0 is the maximum distance between two colors
        // (0,0,0,0 -> 255,255,255,255)
        double percentAway = distance / 510.0d * 100;

        return (percentAway > tolerance);
    }

    public Font createFont(FontType fontType) {
        if (fontType == null) {
            return null;
        }
        return getFont(fontType.getName(), fontType.getStyle(), fontType.getSize());
    }

    public Font getFont(String name, Integer style, Float size) {
        String key = name + "-" + String.valueOf(style) + String.valueOf(size);
        if (fontMap.containsKey(key)) {
            return fontMap.get(key);
        }
        String fontFilePathName = "/dss/fonts/" + name + (name.toLowerCase().endsWith(".ttf") ? "" : ".ttf");
        InputStream is = PdfUtilServiceImpl.class.getResourceAsStream(fontFilePathName);
        Font fontDefault = null;
        try {
            fontDefault = Font.createFont(Font.TRUETYPE_FONT, is);
        } catch (FontFormatException | IOException e) {
            throw new PdfUtilRuntimeException("Exception loading font " + name, e);
        }

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
                .transparentColor(createColor(signatureConfigurationRequest.getTransparentColor()))
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
