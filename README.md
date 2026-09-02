# ExpenseFlow / FinanZero

Sistema fullstack de controle financeiro pessoal, com cadastro de usuários, autenticação, gerenciamento de contas, cartões, ganhos, gastos, dívidas, metas, economias, reembolsos por e-mail e upload de comprovantes em nuvem.

O projeto foi desenvolvido com:

* **Frontend:** React + Vite
* **Backend:** Java 17 + Spring Boot
* **Banco de dados:** PostgreSQL
* **Armazenamento de comprovantes:** Amazon S3
* **Envio de e-mails:** EmailJS
* **Deploy frontend:** Vercel
* **Deploy backend:** Render
* **Banco em produção:** Neon PostgreSQL

## Acesso em produção

Frontend:

```text
https://expense-flow-ashy.vercel.app
```

Backend:

```text
https://expenseflow-backend-vtf2.onrender.com
```

Observação: o backend está hospedado em plano gratuito no Render. Por isso, após algum tempo sem uso, a aplicação pode “dormir”. O primeiro acesso depois de inatividade pode demorar alguns segundos até o serviço acordar.

## Funcionalidades

* Cadastro de usuário com verificação por e-mail.
* Login e autenticação por token.
* Recuperação de senha.
* Separação de dados por usuário logado.
* Cadastro de contas bancárias/carteiras.
* Cadastro de cartões.
* Lançamento de ganhos/receitas.
* Lançamento de gastos variáveis.
* Lançamento de gastos fixos.
* Controle de dívidas e parcelas.
* Controle de metas financeiras.
* Controle de economias.
* Histórico de transações.
* Reembolsos individuais e em lote.
* Envio de solicitação de reembolso por e-mail.
* Upload de comprovantes em PDF, JPG, JPEG e PNG.
* Armazenamento dos comprovantes no Amazon S3.
* Link clicável para abrir comprovante no e-mail.
* Tema claro e escuro no frontend.
* Interface moderna e responsiva.

## Arquitetura

```text
Frontend React/Vite
        ↓
Backend Spring Boot
        ↓
PostgreSQL Neon
        ↓
Amazon S3 para comprovantes
        ↓
EmailJS para envio de e-mails
```

Os dados financeiros e de login ficam no PostgreSQL.

Os arquivos dos comprovantes ficam no Amazon S3, no bucket configurado pela variável:

```env
AWS_S3_BUCKET=finanzero-receipts-gustavo-dev
```

No banco ficam apenas os metadados e a referência do comprovante.

## Estrutura do projeto

```text
ExpenseFlow
├── backend
│   ├── src
│   ├── pom.xml
│   └── Dockerfile
│
├── frontend
│   ├── src
│   ├── package.json
│   └── vite.config.js
│
├── docker-compose.yml
├── README.md
└── .gitignore
```

## Tecnologias utilizadas

### Frontend

* React
* Vite
* JavaScript
* CSS
* Lucide React
* LocalStorage para tema claro/escuro

### Backend

* Java 17
* Spring Boot
* Spring Web
* Spring Data JPA
* Hibernate
* PostgreSQL Driver
* AWS SDK S3
* EmailJS API
* Maven

### Banco e serviços externos

* PostgreSQL local para desenvolvimento
* Neon PostgreSQL em produção
* Amazon S3 para comprovantes
* EmailJS para e-mails
* Render para backend
* Vercel para frontend

## Variáveis de ambiente do backend

O backend precisa das seguintes variáveis:

```env
SPRING_DATASOURCE_URL=
SPRING_DATASOURCE_USERNAME=
SPRING_DATASOURCE_PASSWORD=

EMAILJS_SERVICE_ID=
EMAILJS_TEMPLATE_ID=
EMAILJS_PUBLIC_KEY=
EMAILJS_PRIVATE_KEY=

AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=
AWS_REGION=sa-east-1
AWS_S3_BUCKET=finanzero-receipts-gustavo-dev

APP_CORS_ALLOWED_ORIGIN=
APP_PUBLIC_BASE_URL=
```

Exemplo para produção:

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://HOST_DO_NEON/neondb?sslmode=require
SPRING_DATASOURCE_USERNAME=neondb_owner
SPRING_DATASOURCE_PASSWORD=SENHA_DO_NEON

EMAILJS_SERVICE_ID=service_xxxxx
EMAILJS_TEMPLATE_ID=template_xxxxx
EMAILJS_PUBLIC_KEY=public_key_xxxxx
EMAILJS_PRIVATE_KEY=private_key_xxxxx

AWS_ACCESS_KEY_ID=access_key_xxxxx
AWS_SECRET_ACCESS_KEY=secret_key_xxxxx
AWS_REGION=sa-east-1
AWS_S3_BUCKET=finanzero-receipts-gustavo-dev

APP_CORS_ALLOWED_ORIGIN=https://expense-flow-ashy.vercel.app
APP_PUBLIC_BASE_URL=https://expenseflow-backend-vtf2.onrender.com
```

Nunca envie chaves reais para o GitHub.

## Variáveis de ambiente do frontend

O frontend precisa da URL da API:

```env
VITE_API_URL=https://expenseflow-backend-vtf2.onrender.com/api
```

Localmente, se essa variável não for definida, o frontend usa:

```text
http://localhost:8080/api
```

## Como rodar localmente

### 1. Clonar o repositório

```bash
git clone https://github.com/keijizn/ExpenseFlow.git
cd ExpenseFlow
```

### 2. Criar banco PostgreSQL local

Crie um banco chamado:

```text
finanzero
```

No notebook usado no desenvolvimento, o PostgreSQL local estava na porta `5433`.

### 3. Rodar o backend

Entre na pasta do backend:

```powershell
cd backend
```

Configure as variáveis no PowerShell:

```powershell
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5433/finanzero"
$env:SPRING_DATASOURCE_USERNAME="postgres"
$env:SPRING_DATASOURCE_PASSWORD="postgres"

$env:EMAILJS_SERVICE_ID="seu_service_id"
$env:EMAILJS_TEMPLATE_ID="seu_template_id"
$env:EMAILJS_PUBLIC_KEY="sua_public_key"
$env:EMAILJS_PRIVATE_KEY="sua_private_key"

$env:AWS_ACCESS_KEY_ID="sua_access_key_id"
$env:AWS_SECRET_ACCESS_KEY="sua_secret_access_key"
$env:AWS_REGION="sa-east-1"
$env:AWS_S3_BUCKET="finanzero-receipts-gustavo-dev"

mvn clean spring-boot:run
```

O backend ficará disponível em:

```text
http://localhost:8080
```

### 4. Rodar o frontend

Em outro terminal:

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

O frontend ficará disponível em:

```text
http://localhost:5173
```

## Build local

### Frontend

```powershell
cd frontend
npm.cmd install
npm.cmd run build
```

### Backend

```powershell
cd backend
mvn clean package -DskipTests
```

O arquivo `.jar` será gerado em:

```text
backend/target/finanzero-api-1.0.0.jar
```

## Docker do backend

O backend possui `Dockerfile` para deploy no Render:

```dockerfile
FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

FROM eclipse-temurin:17-jre

WORKDIR /app

COPY --from=build /app/target/finanzero-api-1.0.0.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
```

No Render, o serviço foi configurado como:

```text
Language: Docker
Root Directory: backend
Dockerfile Path: Dockerfile
Branch: main
```

## Deploy

### Backend no Render

Configurações usadas:

```text
Name: expenseflow-backend
Language: Docker
Root Directory: backend
Branch: main
```

URL gerada:

```text
https://expenseflow-backend-vtf2.onrender.com
```

Variável importante de CORS:

```env
APP_CORS_ALLOWED_ORIGIN=https://expense-flow-ashy.vercel.app
```

### Frontend na Vercel

Configurações usadas:

```text
Framework Preset: Vite
Root Directory: frontend
Build Command: npm run build
Output Directory: dist
Install Command: npm install
```

URL gerada:

```text
https://expense-flow-ashy.vercel.app
```

Variável usada:

```env
VITE_API_URL=https://expenseflow-backend-vtf2.onrender.com/api
```

## Amazon S3

Os comprovantes são enviados para o bucket:

```text
finanzero-receipts-gustavo-dev
```

Prefixo usado:

```text
receipts/
```

O bucket deve permanecer privado.

O acesso aos comprovantes é feito por links temporários gerados pelo backend. Os links enviados por e-mail aparecem como botão/link clicável para o usuário.

Foi configurada uma regra de ciclo de vida no S3 para expirar os comprovantes após 30 dias.

## EmailJS

O sistema utiliza EmailJS para:

* verificação de cadastro;
* recuperação de senha;
* solicitação de reembolso.

O template do EmailJS deve conter:

Subject:

```text
{{subject}}
```

To Email:

```text
{{to_email}}
```

From Name:

```text
{{from_name}}
```

Reply To:

```text
{{reply_to}}
```

Corpo do e-mail:

```text
{{{message_html}}}
```

As três chaves em `{{{message_html}}}` são importantes para permitir que o HTML enviado pelo backend seja renderizado corretamente, inclusive os links clicáveis dos comprovantes.

## Observações sobre plano gratuito

Este projeto usa serviços gratuitos para deploy.

Por isso, podem ocorrer limitações:

* O backend no Render pode dormir após inatividade.
* O primeiro acesso depois de algum tempo parado pode ser lento.
* O banco Neon pode levar alguns segundos para acordar se estiver inativo.
* O Amazon S3 pode gerar custos mínimos dependendo do volume de armazenamento e requisições, embora o uso deste projeto seja pequeno.

## Segurança

* Chaves de AWS, EmailJS e banco de dados não devem ser commitadas.
* Use variáveis de ambiente em produção.
* O bucket S3 deve permanecer privado.
* O backend gera links temporários para acesso aos comprovantes.
* O frontend nunca deve acessar diretamente chaves secretas.

## Status do projeto

Versão atual:

```text
v16 - Frontend moderno com tema claro/escuro, backend em Spring Boot, PostgreSQL online, S3 e EmailJS
```

Funcionalidades principais testadas:

* Cadastro e login.
* Verificação por e-mail.
* PostgreSQL salvando dados.
* Upload de comprovante no S3.
* Abertura de comprovante por link temporário.
* E-mail de reembolso com link clicável.
* Deploy backend no Render.
* Deploy frontend na Vercel.

## Autor
Gustao Ignácio

Desenvolvido por Gustavo Ignácio / keijizn.
s


## Atualização v19

- Mantida a correção da conta vinculada às dívidas.
- Recolocados os 3 temas do frontend: Premium dourado, Branco e Preto.
- Mantidas as animações e o visual moderno da v17.
