package ingestao_avaliacao;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;
import ingestao_avaliacao.handler.IngestaoAvaliacaoHandler;
import ingestao_avaliacao.handler.TestContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class LocalRunner {

    private static final String CORPO_PADRAO = "{\"descricao\": \"A pior aula que já vi na vida\", \"nota\": 1}";

    public static void main(String[] args) throws IOException {
        // Aceita o corpo da requisição (JSON) ou o caminho de um arquivo .json como argumento;
        // sem argumento, usa uma avaliação crítica (nota 1)
        String corpo = args.length > 0 ? lerCorpo(args[0]) : CORPO_PADRAO;

        // 1. Instancia a sua Lambda (exige SQS_QUEUE_URL; veja o README)
        IngestaoAvaliacaoHandler handler = new IngestaoAvaliacaoHandler();

        // 2. Simula o evento do API Gateway
        APIGatewayProxyRequestEvent requestEvent = new APIGatewayProxyRequestEvent();
        requestEvent.setBody(corpo);

        // 3. Cria um Context mockado simples para evitar NullPointerException nos logs
        Context context = new TestContext();

        // 4. Executa a função
        APIGatewayProxyResponseEvent response = handler.handleRequest(requestEvent, context);

        // 5. Exibe o resultado no console
        System.out.println("--- RESULTADO DO TESTE LOCAL ---");
        System.out.println("Status Code: " + response.getStatusCode());
        System.out.println("Body: " + response.getBody());
    }

    // No PowerShell as aspas do JSON se perdem ao passar o texto como argumento; um arquivo evita isso.
    // O Windows PowerShell grava UTF-8 com BOM, que não faz parte do JSON e é descartado aqui.
    private static String lerCorpo(String argumento) throws IOException {
        Path arquivo = Path.of(argumento);
        if (!Files.isRegularFile(arquivo)) {
            return argumento;
        }
        String conteudo = Files.readString(arquivo, StandardCharsets.UTF_8);
        return conteudo.startsWith("﻿") ? conteudo.substring(1) : conteudo;
    }
}
