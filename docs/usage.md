# Uso do cliente

## Configuração

```java
import com.raims.RaimsClient;
import java.time.Duration;

RaimsClient raims = RaimsClient.builder()
    .setApiKey(System.getenv("RAIMS_API_KEY"))
    .setTimeout(Duration.ofSeconds(80))
    .setMaxNetworkRetries(0)
    .setRequestObserver(attempt -> recordRequest(attempt))
    .build();
```

O timeout padrão é 80 segundos por tentativa. O limite configurável vai até
300 segundos. `setMaxNetworkRetries` aceita de zero a cinco tentativas extras
e vale somente para leituras. Cada cliente mantém sua configuração; ele pode
ser reutilizado entre threads. Builders são usados por uma thread de cada vez.

`setApiBase` aceita uma origem HTTPS. HTTP é permitido somente em loopback
para validação local. URLs com caminho, credenciais, query ou fragmento são
recusadas. O transporte mantém a validação TLS e bloqueia redirects. Suas
repetições automáticas ficam desativadas por cliente; as tentativas de leitura
são decididas e contabilizadas somente pelo SDK, sem depender de flags da JVM.

`RequestOptions` permite chave de API, idempotência, timeout e retries por
chamada. A chave opcional substitui a credencial somente naquela requisição;
a aplicação deve manter a separação entre empresas e ambientes. O SDK não
oferece seleção de ambiente ou headers arbitrários.

`RaimsClient` implementa `AutoCloseable`. Use `try` com recursos para escopos
curtos ou chame `close()` ao encerrar o cliente compartilhado da aplicação.
O fechamento libera as conexões, cancela requisições em andamento e pode ser
repetido. Depois de fechado, o cliente recusa novas chamadas. Preserve-o aberto
enquanto estiver percorrendo páginas.
O fechamento também acorda as operações que aguardam o prazo de um retry;
elas não precisam esperar esse prazo terminar para concluir o cancelamento.

## Parâmetros e respostas

Os parâmetros tipados ficam em `com.raims.param` e usam `builder()`. Omitir um
setter mantém o campo ausente. `setCampoNull()` envia null quando o contrato
permite. Em atualizações parciais, confira o efeito de null no campo escolhido.
Zero, false, listas e mapas vazios são preservados. `build()` cria uma cópia
independente dos valores; alterações posteriores no builder não a modificam.

Os modelos de resposta ficam em `com.raims.model` e têm getters tipados.
Inteiros usam `Long`, inclusive centavos e segundos Unix, sem conversão para
ponto flutuante. Valores JSON de tipo number usam `BigDecimal`. Objetos
aninhados, listas e mapas mantêm seus tipos. Constantes dos valores públicos
estão em `EnumValues`; a aplicação também pode receber valores futuros.

`hasField(nome)` distingue ausência de presença. `isNull(nome)` identifica
null explícito. `getRawJson()` fornece uma cópia dos campos recebidos,
incluindo campos futuros. Trate esse JSON como dado sensível. A representação
padrão dos recursos omite seu conteúdo.

A estrutura de cada sucesso é conferida contra o status HTTP do contrato
antes de criar o modelo. Campos novos são preservados. Respostas incompatíveis,
inteiros fora de 64 bits, JSON inválido e corpos acima de 16 MiB são recusados.
Permissões, formatos e regras de negócio continuam sujeitos à API.

Quando uma operação admite modelos diferentes por status, o retorno `*Result`
reúne os getters disponíveis. Consulte `getLastResponse().getStatusCode()` e
`hasField` antes de usar um campo específico de um resultado. `202` permanece
pendente. Um resultado sem corpo, como a exclusão de webhook em `204`, ainda
contém os metadados HTTP.

## Paginação

`list()` retorna uma página tipada com `getList()` e `getPage()`. A segunda
propriedade contém os metadados JSON de cursor. `nextPage()` e `previousPage()`
retornam null quando não há página naquela direção. Navegação e iteração
preservam filtros, credenciais e opções da chamada original.

`autoPagingIterable()` busca páginas conforme a aplicação avança. Sair do laço
interrompe novas leituras. `autoPagingToList(limit)` permite limitar a quantidade
de itens carregados. Cursores ausentes ou repetidos com indicação de próxima
página geram erro de protocolo, evitando um ciclo de consultas.
Uma falha ao buscar uma página não avança o iterador: a aplicação pode capturar
a exceção e tentar continuar quando a leitura estiver disponível novamente.

## Arquivos e uploads

Recibos, boletos e permissões de upload são retornados como metadados da API.
O cliente autenticado não baixa os links, não envia arquivos e não encaminha
a chave Bearer para essas URLs. Faça a transferência separadamente pelo
procedimento e pelos headers retornados no contrato do recurso.

## Diagnósticos

O observer recebe somente metadados da tentativa: método, caminho com
parâmetros, duração, tentativa, status e `Request-Id`. Não contém IDs de
recurso, query, corpo ou credencial. Uma falha do observer não altera o
resultado da operação nem provoca uma nova escrita. O SDK não envia telemetria
ou alertas externos. A aplicação pode agregar falhas e recuperação a partir
dessas informações, sem registrar dados pessoais ou financeiros.
