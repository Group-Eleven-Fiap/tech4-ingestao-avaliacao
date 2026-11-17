package ingestao_avaliacao.handler;

import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import ingestao_avaliacao.domain.NotificacaoCritica;
import ingestao_avaliacao.service.AvaliacaoService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngestaoAvaliacaoHandlerTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-08T14:32:01Z"), ZoneOffset.UTC);
    private static final ObjectMapper JSON = new ObjectMapper();

    private final List<String> idsGravados = new ArrayList<>();
    private final List<NotificacaoCritica> publicadas = new ArrayList<>();

    private IngestaoAvaliacaoHandler handler() {
        return new IngestaoAvaliacaoHandler(new AvaliacaoService(
                (id, descricao, nota, urgencia, dataEnvio) -> idsGravados.add(id), publicadas::add, RELOGIO));
    }

    private APIGatewayProxyResponseEvent chamar(IngestaoAvaliacaoHandler handler, String corpo) {
        APIGatewayProxyRequestEvent request = new APIGatewayProxyRequestEvent();
        request.setBody(corpo);
        return handler.handleRequest(request, new TestContext());
    }

    private APIGatewayProxyResponseEvent chamar(String corpo) {
        return chamar(handler(), corpo);
    }

    private static JsonNode corpoDe(APIGatewayProxyResponseEvent resposta) throws Exception {
        // Toda resposta precisa ser um JSON válido
        return JSON.readTree(resposta.getBody());
    }

    @Test
    void registraAvaliacaoComSucessoERetornaOId() throws Exception {
        APIGatewayProxyResponseEvent resposta = chamar("{\"descricao\": \"Aula ótima\", \"nota\": 9}");

        assertEquals(201, resposta.getStatusCode());
        assertEquals("application/json", resposta.getHeaders().get("Content-Type"));
        JsonNode corpo = corpoDe(resposta);
        assertEquals("Avaliação registrada com sucesso!", corpo.get("mensagem").asText());
        assertEquals(idsGravados.get(0), corpo.get("id").asText());
        assertTrue(publicadas.isEmpty());
    }

    @Test
    void avaliacaoCriticaVaiParaAFila() {
        APIGatewayProxyResponseEvent resposta = chamar("{\"descricao\": \"Aula sem áudio\", \"nota\": 2}");

        assertEquals(201, resposta.getStatusCode());
        assertEquals(List.of(new NotificacaoCritica("Aula sem áudio", "CRITICA", "2026-11-08T14:32:01")), publicadas);
    }

    @Test
    void ignoraCamposExtras() {
        assertEquals(201, chamar("{\"descricao\": \"Aula\", \"nota\": 8, \"aluno\": \"Ana\"}").getStatusCode());
    }

    @Test
    void retorna400ParaCorpoAusenteOuEmBranco() throws Exception {
        for (String corpo : new String[]{null, "", "   "}) {
            APIGatewayProxyResponseEvent resposta = chamar(corpo);
            assertEquals(400, resposta.getStatusCode());
            assertEquals("O corpo da requisição é obrigatório.", corpoDe(resposta).get("erro").asText());
        }
    }

    @Test
    void retorna400ParaJsonMalformadoOuTipoIncorreto() throws Exception {
        String[] corpos = {
                "isto não é json",
                "{\"descricao\": \"Aula\", \"nota\": ",
                "{\"descricao\": \"Aula\", \"nota\": \"abc\"}",
                "{\"descricao\": \"Aula\", \"nota\": \"7\"}",   // texto não vira número
                "{\"descricao\": \"Aula\", \"nota\": 7.5}",     // decimal não vira inteiro
                "{\"descricao\": 123, \"nota\": 7}",            // número não vira texto
                "{\"descricao\": \"Aula\", \"nota\": 99999999999}"
        };
        for (String corpo : corpos) {
            APIGatewayProxyResponseEvent resposta = chamar(corpo);
            assertEquals(400, resposta.getStatusCode(), corpo);
            assertTrue(corpoDe(resposta).get("erro").asText().startsWith("JSON inválido"), corpo);
        }
        assertTrue(idsGravados.isEmpty());
    }

    @Test
    void retorna400ParaRegrasDeNegocio() throws Exception {
        assertEquals("A nota deve ser um valor inteiro entre 0 e 10.",
                corpoDe(chamar("{\"descricao\": \"Aula\", \"nota\": 11}")).get("erro").asText());
        assertEquals("A nota deve ser um valor inteiro entre 0 e 10.",
                corpoDe(chamar("{\"descricao\": \"Aula\", \"nota\": -1}")).get("erro").asText());
        assertEquals("O campo nota é obrigatório e deve ser um valor inteiro entre 0 e 10.",
                corpoDe(chamar("{\"descricao\": \"Aula\"}")).get("erro").asText());
        assertEquals("A descrição da avaliação é obrigatória.",
                corpoDe(chamar("{\"descricao\": \"  \", \"nota\": 5}")).get("erro").asText());
        assertEquals("O corpo da requisição é obrigatório.",
                corpoDe(chamar("null")).get("erro").asText());

        assertEquals(400, chamar("{\"nota\": 5}").getStatusCode());
        assertTrue(idsGravados.isEmpty());
    }

    @Test
    void falhaNaFilaRetorna500MasInformaOIdDaAvaliacaoGravada() throws Exception {
        IngestaoAvaliacaoHandler handler = new IngestaoAvaliacaoHandler(new AvaliacaoService(
                (id, descricao, nota, urgencia, dataEnvio) -> idsGravados.add(id),
                notificacao -> {
                    throw new IllegalStateException("SQS indisponível");
                },
                RELOGIO));

        APIGatewayProxyResponseEvent resposta = chamar(handler, "{\"descricao\": \"Aula sem áudio\", \"nota\": 1}");

        assertEquals(500, resposta.getStatusCode());
        JsonNode corpo = corpoDe(resposta);
        assertEquals(idsGravados.get(0), corpo.get("id").asText());
        assertTrue(corpo.get("erro").asText().startsWith("Avaliação registrada, mas"));
    }

    @Test
    void falhaInesperadaRetorna500SemVazarDetalhes() throws Exception {
        IngestaoAvaliacaoHandler handler = new IngestaoAvaliacaoHandler(new AvaliacaoService(
                (id, descricao, nota, urgencia, dataEnvio) -> {
                    throw new IllegalStateException("tabela inexistente: Avaliacoes");
                },
                publicadas::add,
                RELOGIO));

        APIGatewayProxyResponseEvent resposta = chamar(handler, "{\"descricao\": \"Aula\", \"nota\": 8}");

        assertEquals(500, resposta.getStatusCode());
        assertEquals("Erro interno ao processar a avaliação.", corpoDe(resposta).get("erro").asText());
        assertFalse(resposta.getBody().contains("Avaliacoes"));
    }
}
