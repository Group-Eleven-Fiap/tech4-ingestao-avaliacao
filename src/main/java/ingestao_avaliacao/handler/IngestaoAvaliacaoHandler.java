package ingestao_avaliacao.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import ingestao_avaliacao.domain.AvaliacaoRequest;
import ingestao_avaliacao.service.AvaliacaoService;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class IngestaoAvaliacaoHandler implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AvaliacaoService avaliacaoService = new AvaliacaoService();

    @Override
    public APIGatewayProxyResponseEvent handleRequest(APIGatewayProxyRequestEvent request, Context context) {
        context.getLogger().log("Recebendo requisição de avaliação...");

        try {
            AvaliacaoRequest payload = objectMapper.readValue(request.getBody(), AvaliacaoRequest.class);

            // Chama o serviço de regra de negócio
            String idAvaliacao = avaliacaoService.processarAvaliacao(payload, context.getLogger());

            Map<String, Object> respostaMap = new HashMap<>();
            respostaMap.put("mensagem", "Avaliação registrada com sucesso!");
            respostaMap.put("id", idAvaliacao);

            return criarResposta(201, objectMapper.writeValueAsString(respostaMap));

        } catch (IllegalArgumentException e) {
            return criarResposta(400, "{\"erro\": \"" + e.getMessage() + "\"}");
        } catch (Exception e) {
            context.getLogger().log("Erro interno: " + e.getMessage());
            return criarResposta(500, "{\"erro\": \"Erro interno ao processar a avaliação.\"}");
        }
    }

    private APIGatewayProxyResponseEvent criarResposta(int statusCode, String jsonBody) {
        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent();
        response.setStatusCode(statusCode);
        response.setBody(jsonBody);
        response.setHeaders(Collections.singletonMap("Content-Type", "application/json"));
        return response;
    }
}
