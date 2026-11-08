package ingestao_avaliacao;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import ingestao_avaliacao.handler.IngestaoAvaliacaoHandler;
import ingestao_avaliacao.handler.TestContext;

public class LocalRunner {

    public static void main(String[] args) {
        // 1. Instancia a sua Lambda
        IngestaoAvaliacaoHandler handler = new IngestaoAvaliacaoHandler();

        // 2. Simula o evento do API Gateway com um JSON de teste
        APIGatewayProxyRequestEvent requestEvent = new APIGatewayProxyRequestEvent();

        // Testando com nota crítica (ex: nota 3) para ver a lógica de alerta
        requestEvent.setBody("{\"descricao\": \"A pior aula que já vi na vida\", \"nota\": 1}");

        // 3. Cria um Context mockado simples para evitar NullPointerException nos logs
        Context context = new TestContext();

        // 4. Executa a função
        APIGatewayProxyResponseEvent response = handler.handleRequest(requestEvent, context);

        // 5. Exibe o resultado no console da sua IDE
        System.out.println("--- RESULTADO DO TESTE LOCAL ---");
        System.out.println("Status Code: " + response.getStatusCode());
        System.out.println("Body: " + response.getBody());
    }
}
