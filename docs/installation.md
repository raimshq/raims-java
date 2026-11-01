# Instalação e atualização

O SDK usa as coordenadas Maven `com.raims:raims-java` e requer Java 11 ou
superior. A primeira distribuição está no [GitHub](https://github.com/raimshq/raims-java/releases/latest).
Ela inclui o JAR, o POM, fontes, Javadoc, um manifesto e `SHA256SUMS`.
O pacote ainda não está disponível no Maven Central.

## Baixar a release atual

Com GitHub CLI, baixe todos os assets da release estável atual para uma pasta
nova:

```sh
gh release download --repo raimshq/raims-java --pattern '*' --dir raims-java-release
```

Também é possível baixar os arquivos pela página de releases. Os nomes dos
assets permanecem iguais entre versões; a versão e a origem estão no POM e
no manifesto. Antes da instalação, compare os hashes com `SHA256SUMS`.
Em sistemas que oferecem `sha256sum`, execute na pasta baixada:

```sh
sha256sum -c SHA256SUMS
```

No PowerShell, use `Get-FileHash -Algorithm SHA256` para conferir cada arquivo.
O checksum detecta divergência entre arquivos; mantenha também a conferência
do repositório oficial, da tag e do commit da release.

## Instalar no Maven local

Execute a partir da pasta que contém `raims-java-release`:

```sh
mvn install:install-file -Dfile=raims-java-release/raims-java.jar -DpomFile=raims-java-release/raims-java.pom
```

Adicione uma dependência `com.raims:raims-java` ao projeto consumidor, com a
versão declarada no POM baixado. O POM conserva as dependências do SDK, que o
Maven resolve no build da aplicação. Não copie somente o JAR para o classpath
sem resolver essas dependências.

Os assets `raims-java-sources.jar` e `raims-java-javadoc.jar` permitem consultar
o código e a documentação no editor. Eles podem ser anexados à biblioteca
pelo seu ambiente de desenvolvimento.

## Atualizar

Baixe a release estável atual para outra pasta, confira seus hashes e repita
a instalação local. Depois, atualize a versão da dependência no projeto
consumidor e execute os testes da sua integração. O download seleciona a
release atual naquele momento; ele não atualiza projetos automaticamente.

O SDK deve ser usado no servidor. Mantenha a chave de API fora do código, do
navegador e dos logs. A URL-base é a mesma nos dois ambientes; empresa,
ambiente e permissões vêm da chave validada pela API.

## Compilar e validar fontes

Contribuidores podem conferir a geração e preparar um pacote com Python,
Maven e um JDK compatível:

```sh
python scripts/generate.py --check
mvn package
```

O pacote fica em `../.tmp/raims-java-sdk/build`. Esse build não substitui o
gate completo de engenharia. No workspace da Raims, `mvn verify` e
`python scripts/check.py` usam o validador do Developer Toolkit nos JDKs
11, 21 e 25. Os [requisitos de validação](maintenance.md#validação-local)
pertencem à preparação da entrega e não são necessários para consumir o JAR
publicado.
