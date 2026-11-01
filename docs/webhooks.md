# Receber webhooks

Autentique os bytes originais antes de interpretar ou processar o evento.
`WebhookUtility` usa `webhook-id`, `webhook-timestamp` e `webhook-signature`.
O segredo de assinatura é o `secret_key` retornado ao criar ou rotacionar o
webhook e é diferente da chave Bearer da API. Guarde-o em um cofre.

```java
import com.raims.WebhookUtility;
import com.google.gson.JsonObject;
import java.util.Map;

Map<String, String> headers = Map.of(
    "webhook-id", receivedDeliveryId,
    "webhook-timestamp", receivedTimestamp,
    "webhook-signature", receivedSignature);

JsonObject event = WebhookUtility.constructEvent(rawBodyBytes, headers, webhookSecret);
```

`rawBodyBytes` contém os bytes recebidos pelo servidor. Espaços, Unicode e
quebras de linha participam da assinatura; não reserialize o JSON. O método
`verifySignature` autentica sem interpretar o corpo. `constructEvent` também
exige um objeto JSON válido em UTF-8. A aplicação valida os dados do evento.

A tolerância padrão é 300 segundos no passado e no futuro. Mantenha o relógio
sincronizado. Tolerância zero exige o mesmo segundo, e não desativa a proteção.
Nomes de headers ignoram maiúsculas; duplicidades e valores ambíguos são
recusados. Várias assinaturas `v1=` podem coexistir durante a rotação.

Antes de responder com sucesso, confira tipo, ambiente e recurso do evento e
grave o trabalho de forma durável. Deduplicate pelo `id` do corpo; `webhook-id`
identifica a entrega e não substitui o ID do evento. A assinatura válida não
impede reentregas dentro da tolerância. Se a gravação falhar, responda com erro
HTTP para permitir outra entrega. `created_at` no evento usa ISO 8601,
enquanto o timestamp do header usa segundos Unix.

Consulte o [guia de recebimento](https://docs.raims.com/br/integracoes/webhooks/receber-eventos).
