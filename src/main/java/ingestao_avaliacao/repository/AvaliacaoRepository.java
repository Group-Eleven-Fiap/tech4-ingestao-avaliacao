package ingestao_avaliacao.repository;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class AvaliacaoRepository {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName = "Avaliacoes"; // Nome da tabela no DynamoDB

    public AvaliacaoRepository() {
        // Verifica se existe uma variável de ambiente customizada (ex: para o LocalStack)
        String customEndpoint = System.getenv("AWS_ENDPOINT_URL");

        var builder = DynamoDbClient.builder()
                .region(Region.US_EAST_1);

        // Se estiver rodando localmente com LocalStack, apontamos para o localhost:4566
        if (customEndpoint != null && !customEndpoint.isEmpty()) {
            builder.endpointOverride(URI.create(customEndpoint));
            // No LocalStack, as credenciais não precisam ser reais, mas o SDK exige que estejam presentes
            builder.credentialsProvider(software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
                    software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create("test", "test")
            ));
        }

        this.dynamoDbClient = builder.build();
    }

    public void salvar(String id, String descricao, int nota, String urgencia, String dataEnvio) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s(id).build());
        item.put("descricao", AttributeValue.builder().s(descricao).build());
        item.put("nota", AttributeValue.builder().n(String.valueOf(nota)).build());
        item.put("urgencia", AttributeValue.builder().s(urgencia).build());
        item.put("dataEnvio", AttributeValue.builder().s(dataEnvio).build());

        PutItemRequest request = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build();

        dynamoDbClient.putItem(request);
    }
}
