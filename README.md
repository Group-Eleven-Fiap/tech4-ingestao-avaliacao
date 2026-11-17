# Ingestão de Avaliações

Serviço de ingestão de avaliações do Tech Challenge — Fase 4. Uma API recebe a avaliação de uma aula, uma função AWS Lambda a valida e classifica sua urgência, grava os dados no DynamoDB e publica uma mensagem no SQS quando a avaliação é crítica.

## Modelo de cloud e arquitetura

A solução usa **serverless (FaaS) em nuvem pública AWS**: não há servidor para administrar, a escala é automática e o custo acompanha o uso, o que combina com o ambiente de créditos limitados.

```text
Cliente
  │ POST /avaliacao
  ▼
API Gateway (REST, com limite de requisições)
  │ evento proxy
  ▼
IngestaoAvaliacaoHandler (Lambda)
  │ desserializa e valida
  ▼
AvaliacaoService
  ├── DynamoDbAvaliacaoRepository ──► DynamoDB: Avaliacoes
  └── SqsService ───────────────────► SQS: filas-notificacoes-criticas
                                       (somente urgência CRITICA; consumida pelo tech4-notificacao-urgencia)

Falhas ──► Alarme CloudWatch ──► SNS ──► e-mail do administrador
```

| Componente | Motivo |
|------------|--------|
| API Gateway | Endpoint HTTPS público `POST /avaliacao`, com limite de 10 requisições por segundo. |
| AWS Lambda (Java 21, SnapStart) | Executa só quando há avaliação; o SnapStart reduz a partida a frio do Java. |
| DynamoDB | Tabela `Avaliacoes`, sem servidor, com criptografia em repouso e recuperação pontual. |
| Amazon SQS | Desacopla o aviso de urgência: a fila é criada e consumida pelo `tech4-notificacao-urgencia`. |
| CloudWatch + X-Ray | Logs, métricas, alarmes, dashboard e rastreamento. |
| AWS SAM + GitHub Actions | Infraestrutura como código e deploy automatizado. |

Este repositório faz parte de uma solução com três serviços:

| Repositório | Responsabilidade |
|-------------|------------------|
| **tech4-ingestao-avaliacao** (este) | Receber, validar, classificar e gravar a avaliação. |
| [tech4-notificacao-urgencia](https://github.com/Group-Eleven-Fiap/tech4-notificacao-urgencia) | Enviar o e-mail de aviso quando a avaliação é crítica. |
| [tech4-relatorio-semanal](https://github.com/Group-Eleven-Fiap/tech4-relatorio-semanal) | Enviar o relatório semanal com média e quantidades. |

## Documentação da função

**Responsabilidade única:** receber uma avaliação, validá-la, classificá-la e registrá-la. Não envia e-mail e não gera relatório.

| Classe | Responsabilidade |
|--------|------------------|
| `handler.IngestaoAvaliacaoHandler` | Entrada da Lambda; lê o JSON do evento do API Gateway e converte erros em respostas HTTP. |
| `service.AvaliacaoService` | Valida a avaliação, classifica a urgência, gera identificador e data de envio e coordena as integrações. |
| `repository.AvaliacaoRepository` / `repository.DynamoDbAvaliacaoRepository` | Contrato de gravação e implementação com DynamoDB. |
| `service.NotificacaoPublisher` / `service.SqsService` | Contrato de publicação e implementação com SQS. |
| `domain.AvaliacaoRequest` / `domain.UrgenciaEnum` / `domain.NotificacaoCritica` | Corpo da requisição, regra de urgência e mensagem da fila. |
| `exception.*` | Erros de validação (HTTP 400) e falha de aviso à fila (HTTP 500). |

Ponto de entrada: `ingestao_avaliacao.handler.IngestaoAvaliacaoHandler::handleRequest`

## Regras de negócio

1. A descrição da avaliação é obrigatória e não pode ser nula, vazia ou conter somente espaços.
2. A nota é obrigatória e deve ser um inteiro entre `0` e `10`, inclusive.
3. A classificação é determinada pela nota:

   | Nota | Urgência |
   |------|----------|
   | 0 a 4 | `CRITICA` |
   | 5 a 6 | `MEDIA` |
   | 7 a 10 | `BAIXA` |

4. Toda avaliação válida recebe um `id` UUID e um `dataEnvio` gerado no processamento, em **UTC**. Os outros dois serviços convertem para o horário de Brasília.
5. A avaliação é gravada no DynamoDB. Se a urgência for `CRITICA`, também é enviada uma mensagem para a fila SQS.

## API

`POST /avaliacao` (o caminho não usa acento porque o API Gateway não o aceita). A URL completa é o output `UrlDaApi` do deploy.

```json
{
  "descricao": "O atendimento demorou mais do que o esperado.",
  "nota": 3
}
```

A leitura do JSON é estrita: `nota` deve ser um número inteiro (`"3"` e `3.5` são recusados) e `descricao` deve ser texto. Campos extras são ignorados.

| Situação | Status | Corpo |
|----------|--------|-------|
| Avaliação registrada | `201` | `{"mensagem": "Avaliação registrada com sucesso!", "id": "<uuid>"}` |
| Corpo ausente | `400` | `{"erro": "O corpo da requisição é obrigatório."}` |
| JSON inválido ou tipo incorreto | `400` | `{"erro": "JSON inválido ou campos com tipo incorreto: descricao deve ser texto e nota deve ser um inteiro entre 0 e 10."}` |
| Nota ausente | `400` | `{"erro": "O campo nota é obrigatório e deve ser um valor inteiro entre 0 e 10."}` |
| Nota fora do intervalo | `400` | `{"erro": "A nota deve ser um valor inteiro entre 0 e 10."}` |
| Descrição ausente ou em branco | `400` | `{"erro": "A descrição da avaliação é obrigatória."}` |
| Avaliação crítica gravada, mas o aviso à fila falhou | `500` | `{"erro": "Avaliação registrada, mas não foi possível acionar a notificação de urgência.", "id": "<uuid>"}` |
| Falha inesperada | `500` | `{"erro": "Erro interno ao processar a avaliação."}` |

No erro `500` com `id`, a avaliação **já está gravada**: o cliente não deve reenviá-la. O erro não é escondido para que o administrador seja avisado pelo alarme `ingestao-avaliacao-falha-notificacao`.

### Mensagem SQS para avaliação crítica

Para notas de `0` a `4`, o corpo enviado à fila é este (contrato com o `tech4-notificacao-urgencia`):

```json
{
  "descricao": "O atendimento demorou mais do que o esperado.",
  "urgencia": "CRITICA",
  "dataEnvio": "2026-11-08T14:32:01.169"
}
```

O JSON é gerado com o Jackson, então aspas, barras e quebras de linha na descrição são escapadas e a mensagem é sempre válida.

## Configuração

| Variável | Uso | Obrigatória |
|----------|-----|-------------|
| `SQS_QUEUE_URL` | URL da fila de notificações críticas. Definida pelo `template.yaml` com o output do `tech4-notificacao-urgencia`. | Sim |
| `TABELA_AVALIACOES` | Nome da tabela DynamoDB. Padrão: `Avaliacoes`. | Não |
| `AWS_ENDPOINT_URL` | Endpoint alternativo do SDK, usado para apontar ao AWS Emulator. Sem valor, usa os endpoints da AWS. | Não |

As credenciais vêm da cadeia padrão do SDK (na AWS, a role da função); não há chave no código. A região é fixada em `us-east-1`.

## Segurança e governança

- Role IAM exclusiva da função, com menor privilégio: somente `dynamodb:PutItem` na tabela, somente `sqs:SendMessage` na fila de notificações e escrita somente no próprio log group.
- Tabela DynamoDB com criptografia em repouso e recuperação pontual (PITR).
- API com limite de requisições (10 por segundo, rajada de 20) para proteger custo e banco.
- Mensagens de erro não expõem detalhes internos, e a descrição das avaliações não é registrada em log.
- Deploy via OIDC: nenhuma chave da AWS fica guardada no GitHub.

## Monitoramento

| Recurso | O que mostra |
|---------|--------------|
| Logs `/aws/lambda/ingestao-avaliacao` | `ingestao.inicio`, `ingestao.gravada`, `ingestao.notificacao_publicada`, `ingestao.falha_notificacao`, com retenção de 14 dias. |
| Alarme `ingestao-avaliacao-falha-notificacao` | Avaliação crítica gravada cujo aviso não chegou à fila (o administrador não seria notificado). |
| Alarme `ingestao-avaliacao-erros-lambda` | Execução da Lambda encerrada com erro. |
| Alarme `ingestao-avaliacao-api-5xx` | Respostas 5xx da API. |
| Dashboard `ingestao-avaliacao` | Invocações, erros, duração e requisições da API (2xx, 4xx e 5xx). |
| AWS X-Ray | Rastreamento de cada requisição. |

Os alarmes avisam o `AdminEmail` pelo tópico SNS `ingestao-avaliacao-alarmes`. A AWS envia um e-mail de confirmação da inscrição, que precisa ser aceito.

## Deploy

Pré-requisitos, feitos uma única vez na conta AWS e compartilhados com os outros repositórios `tech4-*`:

1. Implantar primeiro o [tech4-notificacao-urgencia](https://github.com/Group-Eleven-Fiap/tech4-notificacao-urgencia): ele cria a fila e exporta a URL e o ARN que este serviço importa.
2. Criar a role de deploy `tech4-github-deploy` com o `infra/github-oidc.yaml` daquele repositório.
3. Neste repositório, em *Settings → Secrets and variables → Actions → Variables*, criar `AWS_ROLE_ARN` e `ADMIN_EMAIL`.

Depois disso, cada push na `main` roda os testes e, se passarem, executa `sam build` e `sam deploy`. Enquanto `AWS_ROLE_ARN` não existir, o pipeline roda só os testes.

> **Primeira implantação em uma conta que já tem esses recursos criados à mão:** o `template.yaml` cria a função `ingestao-avaliacao`, a tabela `Avaliacoes` e a API. Se eles já existirem fora do CloudFormation, o deploy falha por nome duplicado. Apague-os antes (ou exporte os dados da tabela, se houver avaliações a manter).

Deploy manual:

```powershell
sam build
sam deploy --stack-name tech4-ingestao-avaliacao --region us-east-1 --resolve-s3 --capabilities CAPABILITY_IAM --parameter-overrides AdminEmail=admin@exemplo.com
```

Teste da API implantada (use o valor de `UrlDaApi`):

```powershell
curl.exe -X POST "<UrlDaApi>" -H "Content-Type: application/json" --data-binary "@avaliacao.json"
```

## Build e testes

Pré-requisito: JDK 21.

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
```

Os testes cobrem a classificação de urgência, as validações e respostas HTTP, o JSON da fila (inclusive descrição com aspas) e a falha do SQS. Nenhum deles acessa a AWS.

## Execução local com o AWS Emulator

O [AWS Emulator](https://docs.aws.amazon.com/emulator/) serve as APIs do DynamoDB e do SQS na máquina. A função é executada pelo `LocalRunner`, que chama o handler com um evento simulado do API Gateway; o emulador fornece apenas a tabela e a fila.

1. Suba o emulador (no Windows, em Docker, conforme o [guia](https://docs.aws.amazon.com/emulator/latest/userguide/getting-started-docker.html)) e gere o pacote:

   ```powershell
   .\mvnw.cmd package
   ```

2. Aponte o AWS CLI e o SDK para o emulador, na mesma janela do PowerShell. Os arquivos em `~\.aws\emulator\9297\` são gerados a cada início do emulador; o SDK Java precisa de um *trust store* com o certificado dele:

   ```powershell
   $d = "$env:USERPROFILE\.aws\emulator\9297"
   $env:AWS_ENDPOINT_URL = "https://127.0.0.1:9297"
   $env:AWS_SHARED_CREDENTIALS_FILE = "$d\emulator_credentials"
   $env:AWS_CONFIG_FILE = "$d\config"
   $env:AWS_CA_BUNDLE = "$d\ca.pem"
   $env:AWS_EC2_METADATA_DISABLED = "true"
   $env:AWS_REGION = "us-east-1"
   Remove-Item Env:AWS_ACCESS_KEY_ID, Env:AWS_SECRET_ACCESS_KEY, Env:AWS_SESSION_TOKEN, Env:AWS_PROFILE -ErrorAction SilentlyContinue

   keytool -importcert -noprompt -alias aws-emulator -file "$d\ca.pem" -keystore "$env:TEMP\emulator-truststore.p12" -storetype PKCS12 -storepass changeit
   aws sts get-caller-identity   # a conta deve ser 000000000000
   ```

3. Crie a tabela e a fila no emulador:

   ```powershell
   aws dynamodb create-table --table-name Avaliacoes --attribute-definitions AttributeName=id,AttributeType=S --key-schema AttributeName=id,KeyType=HASH --billing-mode PAY_PER_REQUEST
   aws dynamodb wait table-exists --table-name Avaliacoes   # como na AWS, a tabela leva alguns segundos em CREATING
   $env:SQS_QUEUE_URL = aws sqs create-queue --queue-name filas-notificacoes-criticas --query QueueUrl --output text
   ```

4. Envie uma avaliação crítica. O corpo vai em um arquivo porque o PowerShell remove as aspas de argumentos JSON:

   ```powershell
   Set-Content avaliacao.json '{"descricao": "A pior aula que já vi", "nota": 1}' -Encoding utf8
   java "-Djavax.net.ssl.trustStore=$env:TEMP\emulator-truststore.p12" "-Djavax.net.ssl.trustStorePassword=changeit" "-Djavax.net.ssl.trustStoreType=PKCS12" -cp target\ingestao-avaliacao-0.0.1-SNAPSHOT.jar ingestao_avaliacao.LocalRunner avaliacao.json
   ```

5. Confira o resultado:

   ```powershell
   aws dynamodb scan --table-name Avaliacoes
   aws sqs receive-message --queue-url $env:SQS_QUEUE_URL --wait-time-seconds 2
   ```

O estado do emulador fica só em memória: ao reiniciá-lo, repita os passos 2 e 3.

> O emulador atende às chamadas de DynamoDB e SQS desta função. Na versão avaliada (1.0.481), os testes de referência do próprio emulador para funções Lambda Java não passam, por isso o handler roda localmente pelo `LocalRunner`; a execução da Lambda em si é feita na AWS.
