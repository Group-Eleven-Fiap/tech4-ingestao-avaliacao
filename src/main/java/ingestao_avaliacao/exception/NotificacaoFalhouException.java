package ingestao_avaliacao.exception;

/**
 * A avaliação crítica foi gravada, mas o aviso para a fila de notificações falhou.
 * Carrega o id para o cliente saber que a avaliação existe e não reenviá-la.
 */
public class NotificacaoFalhouException extends RuntimeException {

    private final String idAvaliacao;

    public NotificacaoFalhouException(String idAvaliacao, Throwable cause) {
        super("Falha ao publicar a avaliação crítica " + idAvaliacao + " na fila de notificações", cause);
        this.idAvaliacao = idAvaliacao;
    }

    public String getIdAvaliacao() {
        return idAvaliacao;
    }
}
