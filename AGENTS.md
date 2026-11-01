# Raims Java SDK

Leia README, CONTRIBUTING e docs/maintenance.md antes de alterar o SDK.

- Java 11 ou superior, namespace `com.raims` e artefato Maven `com.raims:raims-java`.
- Consuma somente o contrato público fixado em `contracts`, com revisão e
  SHA-256 conferidos. Não importe código dos hosts nem contratos privados.
- `scripts/generate.py` gera serviços, parâmetros, modelos, constantes,
  `RaimsServices`, metadados HTTP e `docs/reference.md`. Não edite esses
  arquivos manualmente. Cada operação deve ser coberta exatamente uma vez.
- Preserve nomes JSON, inteiros em centavos, segundos Unix, omissão e null,
  campos futuros e respostas pendentes. Não gerar idempotência nem repetir
  escritas automaticamente. Retries configuráveis são exclusivos de leitura.
- A chave determina empresa e ambiente. Não ofereça OAuth, seleção de ambiente
  ou headers arbitrários. O transporte não segue redirects, não faz uploads
  nem baixa URLs externas. Preserve a validação TLS.
- Diagnósticos e representações padrão não expõem credenciais, IDs de recurso,
  query ou payload. Metadados de resposta preservam o `Request-Id`.
- Testes, simuladores, ferramentas, dependências de validação, builds e
  evidências ficam exclusivamente em `C:\server\raims\.tmp\raims-java-sdk`.
  Não inclua esses arquivos no repositório ou no pacote.
- Confira a geração offline, compilação Java 11, contrato completo, HTTP,
  paginação, webhooks e consumo do JAR. Use dependências isoladas e não chame
  a API real durante os testes. Encerre processos temporários antes de concluir.
- `mvn verify` e `scripts/check.py` usam o validador canônico do Developer
  Toolkit. Não crie uma suíte paralela no SDK nem trate o bypass interno do
  build recursivo como validação. Preserve a auditoria de dependências.
- O transporte usa política por cliente, com retries automáticos desativados.
  Somente o loop do SDK repete leituras e emite os diagnósticos correspondentes.
  Não depender de flags globais da JVM para impedir repetições de escrita.
- O cliente implementa `AutoCloseable`; preserve fechamento idempotente,
  cancelamento das operações ativas e recusa de chamadas após o fechamento.
- Deixe alterações sem commit e sem conteúdo em staging. Repositório remoto,
  push, release, Maven Central e publicação dos guias têm autorização própria.
- A distribuição inicial usa somente fontes e assets no GitHub. Publicar uma
  release não autoriza Maven Central, workflows executáveis ou infraestrutura.
  Preserve os gates locais e a conferência dos arquivos baixados do destino.
- Descreva os recursos da Raims diretamente e preserve licenças e atribuições
  obrigatórias. Aplique a skill humanizer aos textos destinados a pessoas.
