package ingestao_avaliacao.repository;

public interface AvaliacaoRepository {

    void salvar(String id, String descricao, int nota, String urgencia, String dataEnvio);
}
