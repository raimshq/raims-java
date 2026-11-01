# Segurança

Use o SDK somente no backend e mantenha a chave de API e o segredo de webhook
em armazenamento protegido. Não registre credenciais, corpos, documentos
pessoais ou links temporários. Recursos e parâmetros têm representação
padrão redigida; `getRawJson()` e `toJson()` dão acesso explícito ao conteúdo.

O transporte usa TLS verificado, bloqueia redirects e não transfere arquivos
externos. A chave define empresa e ambiente. Permissões e regras financeiras
são validadas pela API. Valide a assinatura antes de processar um webhook e
deduplicate eventos em armazenamento durável.

Relate uma vulnerabilidade em `security@raims.com`, com versão, impacto e uma
reprodução mínima sem dados reais. Não envie chaves ou informações de clientes
nem publique esses dados em issues abertas.
