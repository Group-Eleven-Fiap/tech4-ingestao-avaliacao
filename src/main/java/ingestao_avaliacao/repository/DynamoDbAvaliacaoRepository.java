package ingestao_avaliacao.repository;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class DynamoDbAvaliacaoRepository implements AvaliacaoRepository {

    private static final String TABELA_PADRAO = "Avaliacoes";

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public DynamoDbAvaliacaoRepository() {
        this(criarCliente(), obterNomeDaTabela());
    }

    DynamoDbAvaliacaoRepository(DynamoDbClient dynamoDbClient, String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    @Override
    public void salvar(String id, String descricao, int nota, String urgencia, String dataEnvio) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("id", AttributeValue.builder().s(id).build());
        item.put("descricao", AttributeValue.builder().s(descricao).build());
        item.put("nota", AttributeValue.builder().n(String.valueOf(nota)).build());
        item.put("urgencia", AttributeValue.builder().s(urgencia).build());
        item.put("dataEnvio", AttributeValue.builder().s(dataEnvio).build());

        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build());
    }

    private static String obterNomeDaTabela() {
        String nome = System.getenv("TABELA_AVALIACOES");
        return nome != null && !nome.isBlank() ? nome : TABELA_PADRAO;
    }

    private static DynamoDbClient criarCliente() {
        var builder = DynamoDbClient.builder().region(Region.US_EAST_1);

        // Endpoint alternativo (ex.: AWS Emulator). As credenciais vêm da cadeia padrão do SDK.
        String customEndpoint = System.getenv("AWS_ENDPOINT_URL");
        if (customEndpoint != null && !customEndpoint.isBlank()) {
            builder.endpointOverride(URI.create(customEndpoint));
        }

        return builder.build();
    }
}
