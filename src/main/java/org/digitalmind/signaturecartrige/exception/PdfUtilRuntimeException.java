package org.digitalmind.signaturecartrige.exception;

public class PdfUtilRuntimeException extends RuntimeException {

    public PdfUtilRuntimeException() {
        super();
    }

    public PdfUtilRuntimeException(String message) {
        super(message);
    }

    public PdfUtilRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }

    public PdfUtilRuntimeException(Throwable cause) {
        super(cause);
    }

    protected PdfUtilRuntimeException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

}
