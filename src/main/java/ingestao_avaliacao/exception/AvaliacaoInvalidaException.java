package ingestao_avaliacao.exception;

/**
 * A requisição não respeita as regras de negócio da avaliação (resulta em HTTP 400).
 */
public class AvaliacaoInvalidaException extends RuntimeException {

    public AvaliacaoInvalidaException(String message) {
        super(message);
    }

    public AvaliacaoInvalidaException(String message, Throwable cause) {
        super(message, cause);
    }
}
