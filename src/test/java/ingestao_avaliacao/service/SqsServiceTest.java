package ingestao_avaliacao.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ingestao_avaliacao.domain.NotificacaoCritica;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqsServiceTest {

    private static final String FILA = "https://sqs.us-east-1.amazonaws.com/000000000000/filas-notificacoes-criticas";

    private final List<SendMessageRequest> enviadas = new ArrayList<>();

    private SqsService service() {
        SqsClient cliente = (SqsClient) Proxy.newProxyInstance(
                getClass().getClassLoader(),
                new Class<?>[]{SqsClient.class},
                (proxy, metodo, args) -> {
                    if (metodo.getName().equals("sendMessage") && args[0] instanceof SendMessageRequest request) {
                        enviadas.add(request);
                        return SendMessageResponse.builder().messageId("m-1").build();
                    }
                    throw new UnsupportedOperationException(metodo.getName());
                });
        return new SqsService(cliente, FILA);
    }

    @Test
    void publicaNaFilaConfiguradaComOsTresCamposDoContrato() throws Exception {
        service().publicar(new NotificacaoCritica("Aula sem áudio", "CRITICA", "2026-11-08T14:32:01.169"));

        assertEquals(1, enviadas.size());
        assertEquals(FILA, enviadas.get(0).queueUrl());
        JsonNode corpo = new ObjectMapper().readTree(enviadas.get(0).messageBody());
        assertEquals("Aula sem áudio", corpo.get("descricao").asText());
        assertEquals("CRITICA", corpo.get("urgencia").asText());
        assertEquals("2026-11-08T14:32:01.169", corpo.get("dataEnvio").asText());
        assertEquals(3, corpo.size());
    }

    @Test
    void descricaoComAspasBarrasEQuebrasDeLinhaContinuaSendoJsonValido() throws Exception {
        String descricao = "O professor disse \"não sei\"\nlinha 2 \\ barra\ttab";

        service().publicar(new NotificacaoCritica(descricao, "CRITICA", "2026-11-08T14:32:01"));

        JsonNode corpo = new ObjectMapper().readTree(enviadas.get(0).messageBody());
        assertEquals(descricao, corpo.get("descricao").asText());
    }
}
