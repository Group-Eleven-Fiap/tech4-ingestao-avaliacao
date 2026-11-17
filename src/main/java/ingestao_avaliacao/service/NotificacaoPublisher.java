package ingestao_avaliacao.service;

import ingestao_avaliacao.domain.NotificacaoCritica;

public interface NotificacaoPublisher {

    void publicar(NotificacaoCritica notificacao);
}
