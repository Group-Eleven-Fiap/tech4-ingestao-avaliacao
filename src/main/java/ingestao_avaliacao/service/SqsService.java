package ingestao_avaliacao.service;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.net.URI;

public class SqsService {

    private final SqsClient sqsClient;
    private final String queueUrl;

    public SqsService() {
        // Pega a URL da fila através de variável de ambiente (ou define o padrão para o LocalStack)
        this.queueUrl = System.getenv("SQS_QUEUE_URL") != null
                ? System.getenv("SQS_QUEUE_URL")
                : "http://localhost:4566/000000000000/filas-notificacoes-criticas";

        String customEndpoint = System.getenv("AWS_ENDPOINT_URL");

        var builder = SqsClient.builder()
                .region(Region.US_EAST_1);

        // Configuração caso esteja rodando localmente com LocalStack
        if (customEndpoint != null && !customEndpoint.isEmpty()) {
            builder.endpointOverride(URI.create(customEndpoint));
            builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create("test", "test")
            ));
        }

        this.sqsClient = builder.build();
    }

    public void enviarNotificacaoCritica(String descricao, String urgencia, String dataEnvio) {
        // Monta o corpo da mensagem com os dados exigidos para o e-mail de aviso de urgência
        String messageBody = String.format(
                "{\"descricao\": \"%s\", \"urgencia\": \"%s\", \"dataEnvio\": \"%s\"}",
                descricao, urgencia, dataEnvio
        );

        SendMessageRequest request = SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(messageBody)
                .build();

        sqsClient.sendMessage(request);
    }
}
