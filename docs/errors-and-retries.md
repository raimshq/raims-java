# Erros, idempotência e retries

As falhas HTTP e de transporte usam exceções em `com.raims.exception`. Confira a categoria
da exceção e os metadados HTTP para decidir a ação da aplicação. Uma falha de
conexão ou timeout depois do envio não comprova que a escrita foi recusada.

O SDK não gera chaves de idempotência e não repete POST, PATCH, PUT ou DELETE
automaticamente. Defina e persista uma chave por intenção, antes da chamada,
e envie-a por `RequestOptions` somente nas operações que a aceitam. Após perder
a resposta, conserve a chave e os mesmos parâmetros; consulte o recurso antes
de criar uma intenção diferente. Alterar dados ou vencimento pode mudar o
significado da operação. Os prazos e a retenção da chave seguem o contrato
da API, sem um cache de idempotência no SDK.

O padrão é não repetir leituras. `setMaxNetworkRetries` permite até cinco
tentativas extras em falhas de rede e respostas transitórias de leitura.
`Retry-After` orienta a espera quando aceito pelo cliente; uma espera acima
do limite suportado retorna a falha para a aplicação decidir quando retomar.
Uma thread interrompida mantém sua interrupção e encerra a operação.
O orçamento inclui todos os envios HTTP: com zero retries, uma chamada faz
no máximo um envio. Cada tentativa feita pelo SDK produz seu próprio
diagnóstico. A política não depende da configuração global de retries da JVM.

Erros fatais da JVM, como `OutOfMemoryError`, são propagados sem conversão em
falha de conexão e sem retry. Eles exigem diagnóstico na aplicação; aumentar
as tentativas não resolve uma falta de memória. O diagnóstico da tentativa
mantém os metadados HTTP disponíveis e não substitui o erro fatal original.

Fechar o cliente também interrompe a espera entre tentativas de leitura.
Uma operação já iniciada termina com `ConnectionException` de cancelamento,
preservando o status e o `Request-Id` da última tentativa quando disponíveis.
Uma nova chamada feita depois de `close()` continua sendo recusada antes do HTTP.

`getLastResponse()` nos recursos e os metadados das exceções preservam status,
`Request-Id`, `Retry-After` e a indicação de replay idempotente quando enviados.
Não registre corpos, documentos, URLs temporárias ou credenciais ao reportar
uma falha. Uma resposta de erro sem o envelope esperado ainda é reportada
como falha HTTP. Um sucesso com estrutura incompatível é falha de protocolo.

HTTP `202` é aceito sem transformá-lo em conclusão. Acompanhe o recurso
pelas consultas e pelos eventos adequados à operação antes de informar
pagamento, cancelamento ou estorno concluído.
