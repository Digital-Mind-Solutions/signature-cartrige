package org.digitalmind.signaturecartrige.exception;

public class PdfUtilException extends Exception {

    public PdfUtilException() {
    }

    public PdfUtilException(String message) {
        super(message);
    }

    public PdfUtilException(String message, Throwable cause) {
        super(message, cause);
    }

    public PdfUtilException(Throwable cause) {
        super(cause);
    }

    public PdfUtilException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
