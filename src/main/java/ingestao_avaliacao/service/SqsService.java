package ingestao_avaliacao.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import ingestao_avaliacao.domain.NotificacaoCritica;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.net.URI;

public class SqsService implements NotificacaoPublisher {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SqsClient sqsClient;
    private final String queueUrl;

    public SqsService() {
        this(criarCliente(), obterVariavel("SQS_QUEUE_URL"));
    }

    SqsService(SqsClient sqsClient, String queueUrl) {
        this.sqsClient = sqsClient;
        this.queueUrl = queueUrl;
    }

    @Override
    public void publicar(NotificacaoCritica notificacao) {
        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(serializar(notificacao))
                .build());
    }

    // O Jackson escapa aspas, quebras de linha e barras, então qualquer descrição gera um JSON válido
    private static String serializar(NotificacaoCritica notificacao) {
        try {
            return OBJECT_MAPPER.writeValueAsString(notificacao);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível serializar a notificação crítica", e);
        }
    }

    // Sem padrão: uma fila ausente deve impedir a função de iniciar, em vez de falhar a cada avaliação crítica
    private static String obterVariavel(String nome) {
        String valor = System.getenv(nome);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Variável de ambiente obrigatória não definida: " + nome);
        }
        return valor;
    }

    private static SqsClient criarCliente() {
        var builder = SqsClient.builder().region(Region.US_EAST_1);

        // Endpoint alternativo (ex.: AWS Emulator). As credenciais vêm da cadeia padrão do SDK.
        String customEndpoint = System.getenv("AWS_ENDPOINT_URL");
        if (customEndpoint != null && !customEndpoint.isBlank()) {
            builder.endpointOverride(URI.create(customEndpoint));
        }

        return builder.build();
    }
}
