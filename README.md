# Ingestão de Avaliações

Serviço de ingestão de avaliações do Tech Challenge — Fase 4. A aplicação recebe avaliações por uma função AWS Lambda, classifica sua urgência, persiste os dados no DynamoDB e publica uma mensagem no SQS quando a avaliação é crítica.

## Arquitetura

```text
Cliente
  │ HTTP (API Gateway / evento proxy)
  ▼
IngestaoAvaliacaoHandler (AWS Lambda)
  │ desserializa e valida
  ▼
AvaliacaoService
  ├── AvaliacaoRepository ──► DynamoDB: Avaliacoes
  └── SqsService ───────────► SQS: fila de notificações críticas
                               (somente urgência CRITICA)
```

- **Entrada:** `IngestaoAvaliacaoHandler`, que implementa o contrato `APIGatewayProxyRequestEvent` → `APIGatewayProxyResponseEvent`.
- **Regra de negócio:** `AvaliacaoService` valida a avaliação, classifica a urgência, gera identificador e data de envio e coordena as integrações.
- **Persistência:** `AvaliacaoRepository` grava os atributos `id`, `descricao`, `nota`, `urgencia` e `dataEnvio` na tabela `Avaliacoes`.
- **Notificação:** `SqsService` envia à fila SQS uma mensagem com `descricao`, `urgencia` e `dataEnvio` para avaliações críticas.
- **Região AWS:** `us-east-1` (definida no código).

## Regras de negócio

1. A descrição da avaliação é obrigatória e não pode ser nula, vazia ou conter somente espaços.
2. A nota é obrigatória e deve ser um inteiro entre `0` e `10`, inclusive.
3. A classificação é determinada pela nota:

   | Nota | Urgência |
   |------|----------|
   | 0 a 4 | `CRITICA` |
   | 5 a 6 | `MEDIA` |
   | 7 a 10 | `BAIXA` |

4. Toda avaliação válida recebe um `id` UUID e um `dataEnvio` gerado no processamento.
5. A avaliação é gravada no DynamoDB. Se a urgência for `CRITICA`, também é enviada uma mensagem para a fila SQS.

**Observações do comportamento atual:** o campo `nota` usa o tipo `Integer` e é obrigatório; se não for enviado, a API retorna `400`.

## Funcionamento da requisição

O handler lê o JSON do campo `body` do evento do API Gateway, converte-o para `AvaliacaoRequest` e chama o serviço. Em caso de sucesso, retorna HTTP `201`; erros de validação retornam `400`; outros erros de processamento ou integração retornam `500`.

### Payload de entrada

O corpo HTTP esperado é:

```json
{
  "descricao": "O atendimento demorou mais do que o esperado.",
  "nota": 3
}
```

Como o handler recebe um evento proxy do API Gateway, uma chamada direta à Lambda deve envolver o JSON no evento, por exemplo:

```json
{
  "httpMethod": "POST",
  "path": "/avaliacoes",
  "headers": {
    "Content-Type": "application/json"
  },
  "body": "{\"descricao\":\"O atendimento demorou mais do que o esperado.\",\"nota\":3}",
  "isBase64Encoded": false
}
```

### Resposta de sucesso

O UUID é gerado em cada processamento:

```json
{
  "mensagem": "Avaliação registrada com sucesso!",
  "id": "8c9c7a6e-381b-4e51-95a8-60743a3fe176"
}
```

### Respostas de validação

Nota fora do intervalo permitido (`400`):

```json
{
  "erro": "A nota deve ser um valor inteiro entre 0 e 10."
}
```

Descrição ausente ou em branco (`400`):

```json
{
  "erro": "A descrição da avaliação é obrigatória."
}
```

Falha inesperada de processamento ou integração (`500`):

```json
{
  "erro": "Erro interno ao processar a avaliação."
}
```

### Mensagem SQS para avaliação crítica

Para notas de `0` a `4`, o corpo enviado à fila tem este formato:

```json
{
  "descricao": "O atendimento demorou mais do que o esperado.",
  "urgencia": "CRITICA",
  "dataEnvio": "2026-11-08T14:32:01.169"
}
```

O campo `dataEnvio` é gerado no momento do processamento; o valor acima é ilustrativo.

## Tecnologias

- Java 21
- Maven
- AWS Lambda Java Core e AWS Lambda Events
- AWS SDK for Java 2.x (DynamoDB e SQS)
- Jackson para leitura e escrita de JSON
- LocalStack para desenvolvimento local dos serviços AWS

## Configuração

| Variável | Uso | Padrão / comportamento |
|----------|-----|------------------------|
| `AWS_ENDPOINT_URL` | Endpoint customizado do AWS SDK, usado para apontar ao LocalStack. Quando definida, o código também configura credenciais locais de teste. | Sem valor, os clientes usam os endpoints AWS padrão e as credenciais fornecidas pelo ambiente AWS. |
| `SQS_QUEUE_URL` | URL da fila usada para notificações críticas. | `http://localhost:4566/000000000000/filas-notificacoes-criticas` |

A tabela DynamoDB é fixada no código como `Avaliacoes`; a aplicação não a cria automaticamente. Para execução local, o LocalStack está configurado em `docker/docker-compose.yml` com os serviços DynamoDB e SQS, mas a tabela e a fila precisam existir antes de processar avaliações.

Exemplo de criação dos recursos locais com AWS CLI:

```powershell
docker compose -f docker/docker-compose.yml up -d

$env:AWS_ACCESS_KEY_ID = "test"
$env:AWS_SECRET_ACCESS_KEY = "test"
$env:AWS_DEFAULT_REGION = "us-east-1"
$env:AWS_ENDPOINT_URL = "http://localhost:4566"

aws dynamodb create-table `
  --table-name Avaliacoes `
  --attribute-definitions AttributeName=id,AttributeType=S `
  --key-schema AttributeName=id,KeyType=HASH `
  --billing-mode PAY_PER_REQUEST `
  --endpoint-url $env:AWS_ENDPOINT_URL `
  --region $env:AWS_DEFAULT_REGION

$queue = aws sqs create-queue `
  --queue-name filas-notificacoes-criticas `
  --endpoint-url $env:AWS_ENDPOINT_URL `
  --region $env:AWS_DEFAULT_REGION `
  --query QueueUrl `
  --output text

$env:SQS_QUEUE_URL = $queue
```

Os comandos acima criam os recursos no LocalStack. Em uma conta AWS, configure a tabela e a fila previamente, defina `SQS_QUEUE_URL` com a URL real da fila e associe à função Lambda permissões para gravar na tabela DynamoDB e enviar mensagens ao SQS. A aplicação não cria API Gateway, função Lambda, tabela ou fila em uma conta AWS.

## Build e testes

Pré-requisitos: JDK 21 e Maven instalado.

```powershell
mvn test
mvn package
```

O ponto de entrada da função é:

```text
ingestao_avaliacao.handler.IngestaoAvaliacaoHandler
```