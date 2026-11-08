package ingestao_avaliacao.service;

import com.amazonaws.services.lambda.runtime.LambdaLogger;
import ingestao_avaliacao.domain.AvaliacaoRequest;
import ingestao_avaliacao.domain.UrgenciaEnum;
import ingestao_avaliacao.repository.AvaliacaoRepository;

import java.time.LocalDateTime;
import java.util.UUID;

public class AvaliacaoService {

    private final AvaliacaoRepository repository = new AvaliacaoRepository();
    private final SqsService sqsService = new SqsService();

    public String processarAvaliacao(AvaliacaoRequest payload, LambdaLogger logger) {
        // 1. Validação de regras de negócio
        if (payload.getNota() == null) {
            throw new IllegalArgumentException("O campo nota é obrigatório e deve ser um valor inteiro entre 0 e 10.");
        }
        if (payload.getNota() < 0 || payload.getNota() > 10) {
            if (logger != null) logger.log("[ERRO_VALIDACAO] Nota inválida informada: " + payload.getNota());
            throw new IllegalArgumentException("A nota deve ser um valor inteiro entre 0 e 10.");
        }
        if (payload.getDescricao() == null || payload.getDescricao().trim().isEmpty()) {
            if (logger != null) logger.log("[ERRO_VALIDACAO] Descrição da avaliação está vazia.");
            throw new IllegalArgumentException("A descrição da avaliação é obrigatória.");
        }

        // 2. Regra de Urgência solicitada
        UrgenciaEnum urgenciaEnum = UrgenciaEnum.fromNota(payload.getNota());
        String urgencia = urgenciaEnum.name(); // Retornará "CRITICA", "MEDIA" ou "BAIXA"

        String dataEnvio = LocalDateTime.now().toString();
        String idAvaliacao = UUID.randomUUID().toString();

        if (logger != null) {
            logger.log(String.format("[INFO] Processando avaliação ID: %s | Nota: %d | Urgencia: %s",
                    idAvaliacao, payload.getNota(), urgencia));
        }

        // 3. Salva no DynamoDB
        repository.salvar(idAvaliacao, payload.getDescricao(), payload.getNota(), urgencia, dataEnvio);
        if (logger != null) {
            logger.log("[SUCESSO] Avaliação salva com sucesso no DynamoDB. ID: " + idAvaliacao);
        }

        // 4. Se for CRITICA, envia para a fila SQS
        if (UrgenciaEnum.CRITICA.equals(urgenciaEnum)) {
            try {
                if (logger != null) logger.log("[ALERTA_CRITICO] Enviando para a fila SQS...");
                sqsService.enviarNotificacaoCritica(payload.getDescricao(), urgencia, dataEnvio);
                if (logger != null) logger.log("[SUCESSO_SQS] Mensagem publicada na fila.");
            } catch (Exception e) {
                // Loga o erro de mensageria sem derrubar o cadastro principal (ou gerencia a resiliência)
                if (logger != null) logger.log("[ERRO_SQS] Falha ao publicar na fila SQS: " + e.getMessage());
            }
        }

        return idAvaliacao;
    }
}
