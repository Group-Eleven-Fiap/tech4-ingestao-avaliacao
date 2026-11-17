package ingestao_avaliacao.repository;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutItemResponse;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DynamoDbAvaliacaoRepositoryTest {

    private final List<PutItemRequest> gravacoes = new ArrayList<>();

    private DynamoDbAvaliacaoRepository repository(String tabela) {
        DynamoDbClient cliente = (DynamoDbClient) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{DynamoDbClient.class},
                (proxy, metodo, args) -> {
                    if (metodo.getName().equals("putItem") && args[0] instanceof PutItemRequest request) {
                        gravacoes.add(request);
                        return PutItemResponse.builder().build();
                    }
                    throw new UnsupportedOperationException(metodo.getName());
                });
        return new DynamoDbAvaliacaoRepository(cliente, tabela);
    }

    @Test
    void gravaOsCincoAtributosNaTabelaConfigurada() {
        repository("Avaliacoes").salvar("id-1", "Aula sem áudio", 3, "CRITICA", "2026-11-08T14:32:01");

        PutItemRequest request = gravacoes.get(0);
        assertEquals("Avaliacoes", request.tableName());
        Map<String, AttributeValue> item = request.item();
        assertEquals(5, item.size());
        assertEquals("id-1", item.get("id").s());
        assertEquals("Aula sem áudio", item.get("descricao").s());
        assertEquals("3", item.get("nota").n());
        assertEquals("CRITICA", item.get("urgencia").s());
        assertEquals("2026-11-08T14:32:01", item.get("dataEnvio").s());
    }
}
