package org.digitalmind.signaturecartrige.service;

import org.digitalmind.signaturecartrige.dto.*;
import org.digitalmind.signaturecartrige.exception.PdfUtilException;
import org.digitalmind.signaturecartrige.sam.SignatureCartridgeRenderer;

import java.io.IOException;
import java.util.List;

public interface PdfUtilService {

    InspectContentResponse inspect(InspectContentRequest request) throws IOException;

    ReplaceContentResponse replace(ReplaceContentRequest request) throws IOException;

    WatermarkContentResponse watermark(WatermarkContentRequest request) throws IOException;

    FlattenContentResponse flatten (FlattenContentRequest request) throws IOException;

    //PrivateContentResponse hasPrivateContent(PrivateContentRequest request) throws IOException;

    boolean match(String fieldNameOrPattern, String fieldName);

    boolean match(String fieldNameOrPattern, List<String> fieldNames);

    SignatureCartridgeResponse createSignatureImage(SignatureCartridgeRequest signatureCartridgeRequest) throws PdfUtilException;

    SignatureCartridgeResponse createSignatureImage(SignatureCartridgeRequest signatureCartridgeRequest, Integer width, Integer height) throws PdfUtilException;

    SignatureCartridgeRenderer createRenderer(SignatureCartridgeRequest signatureCartridgeRequest);
}
