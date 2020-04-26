package org.digitalmind.signaturecartrige.sam;

import org.digitalmind.signaturecartrige.exception.PdfUtilException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface SignatureCartridgeRenderer {

    MultipartFile execute(Integer width, Integer height) throws IOException, PdfUtilException;

}
