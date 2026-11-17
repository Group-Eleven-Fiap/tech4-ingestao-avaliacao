package ingestao_avaliacao.domain;

/**
 * Corpo da mensagem publicada na fila SQS e consumida pelo tech4-notificacao-urgencia.
 * Os nomes dos campos são o contrato entre os dois serviços.
 */
public record NotificacaoCritica(String descricao, String urgencia, String dataEnvio) {
}
