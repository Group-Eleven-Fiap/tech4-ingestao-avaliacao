package ingestao_avaliacao.service;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import ingestao_avaliacao.domain.AvaliacaoRequest;
import ingestao_avaliacao.domain.NotificacaoCritica;
import ingestao_avaliacao.domain.UrgenciaEnum;
import ingestao_avaliacao.exception.AvaliacaoInvalidaException;
import ingestao_avaliacao.exception.NotificacaoFalhouException;
import ingestao_avaliacao.repository.AvaliacaoRepository;
import ingestao_avaliacao.repository.DynamoDbAvaliacaoRepository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;

public class AvaliacaoService {

    private final AvaliacaoRepository repository;
    private final NotificacaoPublisher notificacaoPublisher;
    private final Clock clock;

    public AvaliacaoService() {
        this(new DynamoDbAvaliacaoRepository(), new SqsService(), Clock.systemUTC());
    }

    public AvaliacaoService(AvaliacaoRepository repository, NotificacaoPublisher notificacaoPublisher, Clock clock) {
        this.repository = repository;
        this.notificacaoPublisher = notificacaoPublisher;
        this.clock = clock;
    }

    public String processarAvaliacao(AvaliacaoRequest payload, LambdaLogger logger) {
        // 1. Validação de regras de negócio
        if (payload == null) {
            throw new AvaliacaoInvalidaException("O corpo da requisição é obrigatório.");
        }
        if (payload.getNota() == null) {
            throw new AvaliacaoInvalidaException("O campo nota é obrigatório e deve ser um valor inteiro entre 0 e 10.");
        }
        if (payload.getNota() < 0 || payload.getNota() > 10) {
            logger.log("[ERRO_VALIDACAO] Nota inválida informada: " + payload.getNota());
            throw new AvaliacaoInvalidaException("A nota deve ser um valor inteiro entre 0 e 10.");
        }
        if (payload.getDescricao() == null || payload.getDescricao().trim().isEmpty()) {
            logger.log("[ERRO_VALIDACAO] Descrição da avaliação está vazia.");
            throw new AvaliacaoInvalidaException("A descrição da avaliação é obrigatória.");
        }

        // 2. Regra de urgência
        UrgenciaEnum urgenciaEnum = UrgenciaEnum.fromNota(payload.getNota());
        String urgencia = urgenciaEnum.name(); // "CRITICA", "MEDIA" ou "BAIXA"

        // dataEnvio é sempre UTC: o relatório e a notificação convertem para o horário de Brasília
        String dataEnvio = LocalDateTime.now(clock).toString();
        String idAvaliacao = UUID.randomUUID().toString();

        logger.log(String.format("[INFO] ingestao.inicio id=%s nota=%d urgencia=%s",
                idAvaliacao, payload.getNota(), urgencia));

        // 3. Salva no DynamoDB
        repository.salvar(idAvaliacao, payload.getDescricao(), payload.getNota(), urgencia, dataEnvio);
        logger.log("[SUCESSO] ingestao.gravada id=" + idAvaliacao);

        // 4. Se for CRITICA, publica na fila SQS. A falha não é engolida: a requisição termina com
        //    erro e o alarme "ingestao.falha_notificacao" avisa o administrador.
        if (UrgenciaEnum.CRITICA.equals(urgenciaEnum)) {
            try {
                notificacaoPublisher.publicar(new NotificacaoCritica(payload.getDescricao(), urgencia, dataEnvio));
                logger.log("[SUCESSO_SQS] ingestao.notificacao_publicada id=" + idAvaliacao);
            } catch (RuntimeException e) {
                logger.log(String.format("[ERRO_SQS] ingestao.falha_notificacao id=%s erro=%s",
                        idAvaliacao, e.getClass().getSimpleName()));
                throw new NotificacaoFalhouException(idAvaliacao, e);
            }
        }

        return idAvaliacao;
    }
}
