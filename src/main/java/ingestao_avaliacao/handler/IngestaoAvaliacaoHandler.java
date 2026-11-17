package ingestao_avaliacao.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.LambdaLogger;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.type.LogicalType;
import ingestao_avaliacao.domain.AvaliacaoRequest;
import ingestao_avaliacao.exception.AvaliacaoInvalidaException;
import ingestao_avaliacao.exception.NotificacaoFalhouException;
import ingestao_avaliacao.service.AvaliacaoService;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class IngestaoAvaliacaoHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private static final String ERRO_INTERNO = "Erro interno ao processar a avaliação.";

    // Leitura estrita: "nota": "3" (texto), "nota": 3.5 (decimal) e "descricao": 123 (número) são recusados, não convertidos
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .withCoercionConfig(LogicalType.Textual, config -> config
                    .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail))
            .build();

    private final AvaliacaoService avaliacaoService;

    public IngestaoAvaliacaoHandler() {
        this(new AvaliacaoService());
    }

    IngestaoAvaliacaoHandler(AvaliacaoService avaliacaoService) {
        this.avaliacaoService = avaliacaoService;
    }

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        LambdaLogger logger = context.getLogger();
        logger.log("Recebendo requisição de avaliação...");

        try {
            AvaliacaoRequest payload = lerPayload(request.getBody());

            // Chama o serviço de regra de negócio
            String idAvaliacao = avaliacaoService.processarAvaliacao(payload, logger);

            Map<String, Object> resposta = new LinkedHashMap<>();
            resposta.put("mensagem", "Avaliação registrada com sucesso!");
            resposta.put("id", idAvaliacao);
            return criarResposta(201, resposta);

        } catch (AvaliacaoInvalidaException e) {
            return criarResposta(400, Map.of("erro", e.getMessage()));
        } catch (NotificacaoFalhouException e) {
            // A avaliação foi gravada; o id evita que o cliente a reenvie
            Map<String, Object> resposta = new LinkedHashMap<>();
            resposta.put("erro", "Avaliação registrada, mas não foi possível acionar a notificação de urgência.");
            resposta.put("id", e.getIdAvaliacao());
            return criarResposta(500, resposta);
        } catch (Exception e) {
            logger.log("[ERRO_INTERNO] ingestao.falha erro=" + e.getClass().getSimpleName());
            return criarResposta(500, Map.of("erro", ERRO_INTERNO));
        }
    }

    private AvaliacaoRequest lerPayload(String body) {
        if (body == null || body.isBlank()) {
            throw new AvaliacaoInvalidaException("O corpo da requisição é obrigatório.");
        }
        try {
            return objectMapper.readValue(body, AvaliacaoRequest.class);
        } catch (JsonProcessingException e) {
            throw new AvaliacaoInvalidaException(
                    "JSON inválido ou campos com tipo incorreto: descricao deve ser texto e nota deve ser um inteiro entre 0 e 10.", e);
        }
    }

    private APIGatewayProxyResponseEvent criarResposta(int statusCode, Map<String, Object> corpo) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setStatusCode(statusCode);
        response.setBody(escrever(corpo));
        response.setHeaders(Collections.singletonMap("Content-Type", "application/json"));
        return response;
    }

    private String escrever(Map<String, Object> corpo) {
        try {
            return objectMapper.writeValueAsString(corpo);
        } catch (JsonProcessingException e) {
            return "{\"erro\":\"" + ERRO_INTERNO + "\"}";
        }
    }
}
