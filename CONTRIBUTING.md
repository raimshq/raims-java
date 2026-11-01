# Contribuir

Leia `AGENTS.md` e [manutenção](docs/maintenance.md). Preserve o contrato e
regenere os outputs por `python scripts/generate.py`. Confira a geração
offline com `--check`, compile em Java 11 e valide o JAR, fontes e Javadoc.
`mvn verify` executa o validador canônico do Developer Toolkit nos JDKs 11, 21 e
25. O atalho `python scripts/check.py` também prepara e confere o pacote.
Consulte os caminhos de ferramentas e a auditoria de dependências em
[validação local](docs/maintenance.md#validação-local).

Os cenários independentes devem cobrir todas as operações e status de sucesso,
método, caminho, query, JSON, resultados sem corpo e `202`. Confira também:

- Null, ausência, false, zero, mapas vazios, campos futuros e inteiros exatos.
- Tipos dos builders, respostas, listas, mapas e objetos aninhados.
- Isolamento entre clientes, opções por chamada e chamadas concorrentes.
- Redirects, timeout, interrupção, limite de corpo e UTF-8 inválido.
- Retry somente de leitura, `Retry-After` e ausência de retry em escritas.
- Perda da resposta com contagem de envios e diagnósticos, inclusive quando
  outra biblioteca já tiver inicializado retries globais da JVM.
- Navegação nos dois sentidos, filtros, páginas vazias e ciclos de cursor.
- Recuperação do iterador após falha temporária, fechamento do cliente e
  cancelamento das suas requisições em andamento.
- Schemas usados em entrada e resposta e coleções aninhadas no gerador.
- Referências escalares, aliases anuláveis, itens e mapas referenciados, com
  preservação das constantes públicas em refatorações equivalentes.
- Erros fatais do worker preservados sem retry e cancelamento da espera de
  retry por fechamento ou interrupção, conservando os metadados disponíveis.
- Webhooks com bytes originais, segredo Base62, relógio, rotação e headers ambíguos.
- Diagnósticos redigidos e observers que falham ou iniciam outras chamadas.

Use receptores em loopback e dependências isoladas. Testes, simuladores,
consumidores, ferramentas e relatórios ficam somente em
`C:\server\raims\.tmp\raims-java-sdk`. Não chame a API real, mesmo com
chave Sandbox, para validar a biblioteca. Encerre processos e confira portas.

Revise todos os arquivos modificados e novos, `git diff` e `git diff --cached`.
Preserve licenças e atribuições. Mantenha alterações sem commit e sem staging;
criação de remoto, push, release e publicação são etapas independentes.
