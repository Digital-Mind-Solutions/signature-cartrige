package org.digitalmind.signaturecartrige.sam.impl;

import org.apache.commons.io.IOUtils;
import org.digitalmind.signaturecartrige.dto.SignatureCartridgeRequest;
import org.digitalmind.signaturecartrige.dto.SignatureCartridgeResponse;
import org.digitalmind.signaturecartrige.exception.PdfUtilException;
import org.digitalmind.signaturecartrige.sam.SignatureCartridgeRenderer;
import org.digitalmind.signaturecartrige.service.PdfUtilService;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class SignatureCartridgeRendererImpl implements SignatureCartridgeRenderer {

    private final PdfUtilService pdfUtilService;
    private final SignatureCartridgeRequest signatureCartridgeRequest;

    public SignatureCartridgeRendererImpl(
            PdfUtilService pdfUtilService,
            SignatureCartridgeRequest signatureCartridgeRequest
    ) {
        this.pdfUtilService = pdfUtilService;
        this.signatureCartridgeRequest = signatureCartridgeRequest;
    }

    @Override
    public MultipartFile execute(Integer width, Integer height) throws IOException, PdfUtilException {
        SignatureCartridgeResponse cartridgeResponse = pdfUtilService.createSignatureImage(signatureCartridgeRequest, width, height);
        MultipartFile signatureCartridgeImage = toMultipartFile(
                cartridgeResponse.getResource().getInputStream(),
                signatureCartridgeRequest.getSession() + "." + signatureCartridgeRequest.getConfiguration().getImageType().name().toLowerCase(),
                signatureCartridgeRequest.getSession() + "." + signatureCartridgeRequest.getConfiguration().getImageType().name().toLowerCase(),
                cartridgeResponse.getContentType(),
                0);
        return signatureCartridgeImage;
    }

    private MultipartFile toMultipartFile(InputStream inputStream, String name, String originalFilename, String contentType, long length) {
        byte[] byteArray = null;
        try {
            byteArray = IOUtils.toByteArray(inputStream);
        } catch (IOException e) {
            throw new UnsupportedOperationException("Multipart conversion from input stream");
        }

        byte[] finalByteArray = byteArray;
        return new MultipartFile() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String getOriginalFilename() {
                return originalFilename;
            }

            @Override
            public String getContentType() {
                return contentType;
            }

            @Override
            public boolean isEmpty() {
                return getSize() <= 0;
            }

            @Override
            public long getSize() {
                return finalByteArray.length;
            }

            @Override
            public byte[] getBytes() throws IOException {
                return finalByteArray;
                //throw new UnsupportedOperationException("Anonymous multipart file getBytes is not supported");
            }

            @Override
            public InputStream getInputStream() throws IOException {
                //return inputStream;
                return new ByteArrayInputStream(finalByteArray);
            }

            @Override
            public void transferTo(File dest) throws IOException, IllegalStateException {
                throw new UnsupportedOperationException("Anonymous multipart file transferTo is not supported");
            }
        };
    }

}
