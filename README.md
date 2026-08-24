# FinanZero - v16 Front moderno com tema claro/escuro + PostgreSQL + S3

Tema claro/escuro, login/cadastro, verificação de e-mail por código, recuperação de senha, envio real por EmailJS, reembolsos individuais e em lote, filtro mensal, relatórios, cartões/faturas, **PostgreSQL local** como banco principal e **Amazon S3** para armazenar os comprovantes.

## O que fica salvo onde

**PostgreSQL local:**

- usuários;
- contas bancárias;
- categorias;
- ganhos;
- despesas fixas e variáveis;
- dívidas;
- economias/investimentos;
- metas;
- reembolsos;
- metadados dos comprovantes, como nome do arquivo, nome original e tipo do arquivo.

**Amazon S3:**

- arquivos PDF/PNG/JPG/JPEG dos comprovantes.

O bucket deve ficar privado. A rota do backend `/api/receipts/{arquivo}` gera um link temporário do S3 para abrir o comprovante. A regra de ciclo de vida do bucket deve apagar automaticamente os objetos do prefixo `receipts/` depois de 30 dias.

## Preparar o PostgreSQL

Crie no pgAdmin um banco chamado:

```text
finanzero
```

Owner recomendado:

```text
postgres
```

## Preparar o S3

Bucket usado nesta versão:

```text
finanzero-receipts-gustavo-dev
```

Região:

```text
sa-east-1
```

Prefixo usado pelo sistema:

```text
receipts/
```

Configuração recomendada do bucket:

- Block all public access: ligado;
- ACLs desabilitadas;
- versionamento: desativado;
- criptografia SSE-S3;
- Lifecycle rule para expirar objetos em `receipts/` após 30 dias.

Permissões mínimas do usuário IAM:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Sid": "FinanZeroListBucket",
      "Effect": "Allow",
      "Action": ["s3:ListBucket"],
      "Resource": "arn:aws:s3:::finanzero-receipts-gustavo-dev",
      "Condition": {
        "StringLike": {
          "s3:prefix": ["receipts/*"]
        }
      }
    },
    {
      "Sid": "FinanZeroReceiptObjects",
      "Effect": "Allow",
      "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
      "Resource": "arn:aws:s3:::finanzero-receipts-gustavo-dev/receipts/*"
    }
  ]
}
```

## Como rodar o backend

No PowerShell:

```powershell
cd backend

$env:EMAILJS_SERVICE_ID="service_xxxxx"
$env:EMAILJS_TEMPLATE_ID="template_xxxxx"
$env:EMAILJS_PUBLIC_KEY="sua_public_key"
$env:EMAILJS_PRIVATE_KEY="sua_private_key"

$env:AWS_ACCESS_KEY_ID="sua_access_key_id"
$env:AWS_SECRET_ACCESS_KEY="sua_secret_access_key"
$env:AWS_REGION="sa-east-1"
$env:AWS_S3_BUCKET="finanzero-receipts-gustavo-dev"

mvn clean spring-boot:run
```

Nesta versão, o `application.properties` já está configurado para PostgreSQL local com:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/finanzero
spring.datasource.username=postgres
spring.datasource.password=postgres
```

Se sua senha do PostgreSQL for diferente, altere o arquivo ou sobrescreva por variável de ambiente.

## Como rodar o frontend

Em outro PowerShell:

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

Acesse:

```text
http://localhost:5173
```

## Como zerar o banco PostgreSQL local

No pgAdmin, abra o Query Tool do banco `finanzero` e execute:

```sql
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
```

Depois rode o backend novamente. O Hibernate recria as tabelas automaticamente.

Para apagar comprovantes de teste do S3 antes da lifecycle rule, entre no bucket e exclua os objetos dentro de `receipts/`, ou use a tela do S3.

## EmailJS

O projeto usa EmailJS para envio real de e-mails.

Configuração em `application.properties`:

```properties
app.mail.enabled=true
app.mail.from-name=FinanZero
app.mail.reply-to=noreply@finanzero.local
app.mail.emailjs.service-id=${EMAILJS_SERVICE_ID:}
app.mail.emailjs.template-id=${EMAILJS_TEMPLATE_ID:}
app.mail.emailjs.public-key=${EMAILJS_PUBLIC_KEY:}
app.mail.emailjs.private-key=${EMAILJS_PRIVATE_KEY:}
app.mail.emailjs.base-url=https://api.emailjs.com/api/v1.0/email/send
```

Template recomendado no EmailJS:

```text
Para: {{to_email}}
Assunto: {{subject}}
Corpo do e-mail: {{{message_html}}}
```

Use um template genérico com `{{{message_html}}}` no corpo. As três chaves são necessárias para o EmailJS renderizar HTML, como o botão/link clicável do comprovante. O backend também envia `message` para compatibilidade, mas o botão do comprovante depende de `message_html`.

## Reembolsos e comprovantes

Despesas fixas e variáveis podem ser marcadas como reembolsáveis.

Fluxo:

1. Ao cadastrar despesa, marque `Gasto reembolsável`.
2. Informe empresa e e-mail de destino.
3. Opcionalmente, anexe um comprovante em PDF, PNG, JPG ou JPEG.
4. O backend envia o comprovante para `s3://finanzero-receipts-gustavo-dev/receipts/`.
5. O PostgreSQL salva os metadados do comprovante na transação.
6. A tela `Reembolsos` lista esses gastos.
7. Você pode enviar um reembolso individualmente ou selecionar vários e usar `Enviar selecionados`.
8. O e-mail inclui links para abrir os comprovantes.
9. Ao clicar no link, o backend gera um link temporário do S3.
10. Quando o valor cair na conta, escolha a conta e clique em `Marcar recebido`.
11. O sistema cria automaticamente uma entrada do tipo ganho/reembolso e soma no saldo da conta.

Localmente, os links dos comprovantes enviados por e-mail dependem do backend local estar rodando. Em produção, troque `app.public-base-url` pelo domínio público do backend.

## Regras financeiras

- Ganhos somam no saldo da conta.
- Pix e Débito reduzem saldo.
- Crédito reduz limite disponível do cartão.
- Despesa fixa só impacta saldo/limite quando marcada como paga.
- Dívidas controlam pagamento por parcela e não aceitam pagamento maior que o restante.
- Economias reduzem saldo da conta de origem e podem ser atualizadas.
- Metas permitem atualizar o valor atual.
- Categorias possuem seleção maior de ícones.
