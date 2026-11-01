# Raims Java

SDK Java para integrar aplicações de servidor à API pública da Raims.
Requer Java 11 ou superior e usa Apache HttpComponents para HTTP e Gson para JSON.

## Instalação pelo GitHub

A distribuição inicial usa as [releases deste repositório](https://github.com/raimshq/raims-java/releases/latest).
O pacote ainda não está publicado no Maven Central.

Baixe os arquivos da release estável atual com GitHub CLI:

```sh
gh release download --repo raimshq/raims-java --pattern '*' --dir raims-java-release
```

Instale o JAR e seu POM no repositório Maven local:

```sh
mvn install:install-file -Dfile=raims-java-release/raims-java.jar -DpomFile=raims-java-release/raims-java.pom
```

Adicione `com.raims:raims-java` às dependências do seu projeto, usando a versão
declarada no POM baixado. O Maven resolve as dependências transitivas pelo POM.
A aplicação consumidora precisa de Java 11 ou superior; a instalação do JAR
não exige Python nem o Toolkit de engenharia.

Veja a [instalação e atualização](docs/installation.md) para conferir os hashes,
usar fontes e Javadoc e atualizar uma instalação existente.

## Primeiro uso

```java
import com.raims.RaimsClient;
import com.raims.model.CustomerListResponse;
import com.raims.param.ListCustomersParams;

try (RaimsClient raims = new RaimsClient(System.getenv("RAIMS_API_KEY"))) {
    CustomerListResponse customers = raims.customers().list(
        ListCustomersParams.builder().setPageSize(20).build());

    customers.getList().forEach(customer -> System.out.println(customer.getId()));
}
```

A URL-base é `https://api.raims.com` tanto para Sandbox quanto para Produção.
A chave determina empresa, ambiente e permissões. Guarde-a no servidor, fora
do código, do navegador e dos logs. Reutilize o cliente entre requisições e
feche-o ao encerrar sua aplicação ou o escopo de uso.

## Criar uma cobrança

`customerId`, `chargeDueDate` e `chargeIdempotencyKey` devem ser definidos e
persistidos pela aplicação para o pedido antes da chamada. O vencimento usa
segundos Unix UTC. Após perder uma resposta, reutilize a mesma chave e todos
os parâmetros, inclusive o vencimento.

```java
import com.raims.RaimsClient;
import com.raims.RequestOptions;
import com.raims.model.ChargeCreated;
import com.raims.param.CreateChargeParams;

try (RaimsClient raims = new RaimsClient(System.getenv("RAIMS_API_KEY"))) {
    ChargeCreated charge = raims.charges().create(
        CreateChargeParams.builder()
            .setCustomerId(customerId)
            .setAmountCents(15000)
            .setDueDate(chargeDueDate)
            .setTitle("Pedido 1234")
            .build(),
        RequestOptions.builder().setIdempotencyKey(chargeIdempotencyKey).build());

    int httpStatus = charge.getLastResponse().getStatusCode();
    String requestId = charge.getLastResponse().getRequestId();
}
```

`15000` representa R$ 150,00. O SDK não gera chaves de idempotência nem repete
escritas automaticamente. HTTP `202` indica um resultado pendente: acompanhe
o recurso para conhecer a conclusão financeira.

## Paginação

```java
try (RaimsClient raims = new RaimsClient(System.getenv("RAIMS_API_KEY"))) {
    for (var customer : raims.customers().list().autoPagingIterable()) {
        processCustomer(customer);
    }
}
```

O iterador lê as próximas páginas quando necessário e preserva filtros e
opções. `nextPage()` e `previousPage()` permitem navegação explícita.
`autoPagingToList(limit)` reúne até o limite escolhido pela aplicação.

## Recursos

`accounts()`, `balances()`, `customers()`, `charges()`, `paymentLinks()`,
`refunds()`, `disputes()`, `statements()`, `withdrawals()`,
`withdrawalDestinations()`, `webhooks()`, `webhookEventTypes()`,
`simulations().charges()` e `simulations().refunds()`.

Use chave Sandbox para as simulações. Os serviços seguem métodos como
`create`, `retrieve`, `update` e `list`, conforme o recurso.

## Documentação

- [Instalação e atualização](docs/installation.md)
- [Configuração, tipos, respostas e arquivos](docs/usage.md)
- [Erros, idempotência e retries](docs/errors-and-retries.md)
- [Recebimento de webhooks](docs/webhooks.md)
- [Referência dos métodos](docs/reference.md)
- [Contrato e manutenção](docs/maintenance.md)
- [Contribuição](CONTRIBUTING.md) e [segurança](SECURITY.md)
- [Documentação da API](https://docs.raims.com/br/api)

Distribuído sob a licença [MIT](LICENSE).
