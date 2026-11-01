# TODO

- [ ] Definir a API Java estável antes da primeira distribuição. Identificar
  classes, métodos, assinaturas e constantes protegidos por compatibilidade,
  excluir os helpers internos dessa garantia e acrescentar uma base aprovada
  ao validador canônico para conferir futuras versões.
- [ ] Definir a interface tipada dos metadados de paginação e o retorno de
  `nextPage()` e `previousPage()` antes da primeira distribuição. Preservar
  filtros, opções, null, navegação e recuperação após falhas; validar os
  consumidores e atualizar os guias quando essa evolução for implementada.
- [ ] Medir o consumo do executor e das conexões sob cargas isoladas antes de
  oferecer configuração de recursos. Manter testes e evidências em `.tmp` e
  escolher a política pelas medições, preservando timeout, cancelamento e a
  proibição de retries automáticos de escrita.
- [ ] Automatizar futuras releases do GitHub nos runners autorizados, vinculando
  o mesmo commit aos checks, aos hashes dos assets e ao teste de instalação.
  Essa automação não deve publicar no Maven Central.
- [ ] Preparar a distribuição Maven Central: confirmar titularidade do namespace
  `com.raims`, artefato `raims-java`, assinatura e fluxo de CI nos runners
  autorizados. Validar o mesmo commit e conferir uma instalação limpa do
  destino após publicação autorizada.
- [ ] Incluir o SDK Java nas fontes do atendimento na primeira distribuição.
  Criar os guias BR/EN `integracoes/sdk-java` no `raims-developer-docs`, com
  finalidade, quando sugerir Java, instalação e atualização, backend, acesso,
  permissões, ambiente pela chave, simulações, disponibilidade e limites,
  paginação, idempotência, resultados pendentes, erros, webhooks e fechamento
  do cliente compartilhado. Conferir
  `Raims-Chat/docs/knowledge-sources.md`, regenerar o catálogo público existente
  e validar a orientação nos dois idiomas. A biblioteca não cria estados do
  atendimento ou consultas financeiras: não exige alteração no contrato de
  contexto ou nas ferramentas atuais. Concluir somente após confirmar pacote,
  fontes publicadas, catálogo e orientação.
- [ ] Publicar a nota de changelog Java em
  `raims-developer-docs/content/docs/changelog/post`, com data real da
  disponibilização e links para os guias e a versão confirmada. A implementação
  local permanece pendente, sem anúncio de disponibilidade pública.
