package es.aadtex;

import java.io.IOException;

/** Contenido inválido: se aísla. Los fallos de disco se conservan para reintentar. */
public class BatchValidationException extends IOException {
    public BatchValidationException(String message) { super(message); }
    public BatchValidationException(String message, Throwable cause) { super(message, cause); }
}
