# Contrato e manutenção

## Fonte e geração

`contracts/openapi.json` preserva os bytes da revisão pública imutável
identificada em `contracts/source.json`, com URL, revisão, SHA-256 e versão.
A fonte canônica pertence à Public API; o SDK consome a superfície publicada
em [raimshq/openapi](https://github.com/raimshq/openapi).

```sh
python scripts/generate.py
python scripts/generate.py --check
```

O gerador é offline, confere o checksum e exige que `contracts/service-map.json`
cubra cada operação exatamente uma vez. Gera serviços, parâmetros, modelos,
constantes, composição dos serviços, metadados HTTP e referência. `--check`
compara as saídas sem regravá-las e detecta arquivos gerados obsoletos.

Entradas e respostas têm representações próprias mesmo quando compartilham
um schema. Listas e mapas aninhados preservam os tipos Java; as descrições dos
parâmetros de query também entram no Javadoc dos builders.
Referências a tipos escalares, listas e mapas usam o tipo Java correspondente,
inclusive em cadeias de aliases anuláveis. Uma extração de enum para um
componente mantém as constantes derivadas do campo que o utiliza. Respostas
raiz de operações continuam sendo objetos; um contrato que mude essa condição
é recusado pelo gerador e exige uma decisão própria de compatibilidade.

Ao atualizar o snapshot, confira manifesto e checksum da revisão pública antes
de mudar os arquivos de origem. Revise tipos, nulabilidade, campos obrigatórios,
autenticação, ambiente, status, idempotência e cursores. Regere as saídas e
confira o impacto nos consumidores. Não altere o contrato para inventar um
endpoint ou contornar uma restrição da API.

## Organização

| Caminho | Responsabilidade |
| --- | --- |
| `RaimsClient` | Configuração isolada e composição dos serviços |
| `service` | Métodos tipados por recurso, gerados |
| `param` | Entradas imutáveis com builders, geradas |
| `model` | Respostas e objetos aninhados, gerados |
| `internal/ApiRequestor` | Transporte HTTP, leitura, retries e erros |
| `ApiResource` e `ApiParams` | Preservação do JSON e acesso aos tipos |
| `RaimsCollection` | Cursores e iteração |
| `WebhookUtility` | Autenticação do corpo original |
| `src/main/resources/com/raims/contract.json` | Metadados gerados usados na validação |

## Impacto desta implementação

| Comportamento | Fonte | Consumidores | Persistência | Validação |
| --- | --- | --- | --- | --- |
| Novo cliente Java da API pública | Contrato público fixado | Aplicações Java | Nenhuma migration | Todas as operações e status publicados |
| Paginação, erros e respostas pendentes | Contrato HTTP existente | Serviços e modelos Java | Nenhuma | Transporte local e cenários de protocolo |
| Recebimento de eventos | Protocolo de assinatura publicado | Backend do integrador | Deduplicação pertence ao integrador | Bytes originais, relógio e rotação |
| Orientação do atendimento | Guias públicos na primeira distribuição | Catálogo existente do chat | Nenhuma | Pendente até disponibilidade pública |

A entrega não altera o contrato nem o comportamento da Public API. Os
consumidores CLI, MCP e SDKs existentes continuam no mesmo contrato; não
precisam ser regenerados por causa deste cliente. O SDK usa somente chave de
API, e os parâmetros exclusivos de OAuth não entram nas assinaturas Java.

## Validação local

`mvn verify` confere os arquivos gerados e chama
`Developer-Toolkit/scripts/release-preflight/java_sdk.py`. O launcher
`python scripts/check.py` usa o mesmo adaptador para preparar e validar o
pacote. O Maven não mantém uma segunda suíte: os arquivos Java de teste,
fixtures, consumidores e evidências são materializados somente em `.tmp`.

Prepare Maven e os JDKs 11, 21 e 25. O adaptador aceita `--maven` e
`--jdk-home 11=caminho`, `--jdk-home 21=caminho` e `--jdk-home 25=caminho`.
Também reconhece `RAIMS_MAVEN`, `RAIMS_MAVEN_HOME`, `RAIMS_JAVA_11_HOME`,
`RAIMS_JAVA_21_HOME` e `RAIMS_JAVA_25_HOME`, além das ferramentas já preparadas
em `.tmp/raims-java-sdk/tools`. Não instala ferramentas globalmente.

O gate cobre cada operação e status publicado, transporte real em loopback,
perda de resposta, orçamento de retries, paginação após falha, concorrência,
cancelamento, fechamento, webhooks e os formatos de schema que causaram as
regressões do gerador. Confere os artefatos e as dependências efetivas do
pacote, incluindo transitivas, contra a base OSV. Erro de consulta ou
vulnerabilidade impede aprovação; não há chamada à API financeira real.
Há regressões para preservar erros fatais sem retry, inclusive falta de
memória em uma JVM isolada, e para fechar o cliente antes e durante a espera
entre tentativas, com várias operações ou interrupção da thread. As cópias
de contrato também conferem referências escalares e estabilidade das constantes.

O modo interno `--verify-built` recebe o JAR já produzido pelo Maven e seu
classpath de dependências, evitando um build recursivo. A opção interna
`raims.skipSdkCheck` serve ao build conduzido pelo próprio adaptador; ela não
aprova o pacote nem desativa a conferência dos gerados. Use o gate completo
para avaliar uma entrega. Fontes alteradas durante a rodada invalidam o aceite.

## Build e distribuição

Use Java 11 ou superior e Maven. `mvn package` gera JAR, fontes e Javadoc,
com outputs em `../.tmp/raims-java-sdk/build`. Dependências e consumidores
de validação também ficam em `.tmp`. Inspecione os três artefatos e confira
o consumo de um JAR instalado, além do build do próprio projeto.

A distribuição inicial usa `raimshq/raims-java`, com fontes na `main` e
releases versionadas no GitHub. O artefato Maven tem coordenadas
`com.raims:raims-java`. JAR, POM, fontes, Javadoc, manifesto e hashes são
conferidos após o download da release. A instalação do JAR com seu POM não
depende do Toolkit privado.

O perfil de publicação de fontes não contém workflows executáveis nem altera
infraestrutura. Execute os gates `Commit` e `Deploy` antes do commit e da
publicação, com SCA, SAST e origem conferidos. A autorização desta etapa é
somente GitHub. Maven Central, assinatura e configuração de CI CodeBuild
continuam exigindo sua preparação e autorização próprias. Não anuncie o
pacote no Maven Central antes de confirmar a versão e sua instalação.

Na primeira distribuição, publique os guias BR/EN `integracoes/sdk-java` no
`raims-developer-docs`, usando a versão realmente disponível. Registre o
changelog na data efetiva e regenere o catálogo público que já alimenta o
atendimento. Valide a orientação nos dois idiomas. O contrato de contexto
e as ferramentas financeiras não mudam por causa da biblioteca. As pendências
permanecem em `TODO.md` até confirmar pacote, fontes e orientação publicada.
