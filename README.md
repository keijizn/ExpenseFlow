# Reembolso Clean

Sistema moderno para lançamento de despesas com login, cadastro, anexos de recibo/comprovante, controle de status, perfil administrador, filtros e envio mensal automático por e-mail.

## O que vem pronto

- Cadastro e login de usuários.
- Perfil de usuário comum e administrador.
- Usuário comum vê apenas os próprios lançamentos.
- Administrador vê todos os lançamentos, altera status, exporta CSV e envia relatório.
- Lançamento de despesa com valor, descrição, data, categoria e anexo opcional em PDF/imagem.
- Categorias: Cartório, Transporte, Alimentação, Material, Serviço e Outros.
- Status: Pendente, Enviado para reembolso, Reembolsado e Recusado.
- Painel mensal com total, quantidade, último lançamento e pendências.
- Filtro por mês, status e categoria.
- Prévia do e-mail antes do envio.
- Exportação CSV para abrir no Excel.
- Botão para enviar relatório por e-mail manualmente.
- Envio automático no dia 1º de cada mês, às 08h, com o resumo do mês anterior.
- Banco SQLite local.

## Como rodar

### 1. Backend

```bash
cd backend
npm install
cp .env.example .env
npm run init-db
npm run dev
```

Edite o arquivo `.env` com os dados do e-mail SMTP, destinatário do relatório e e-mails administradores.

### 2. Administradores

No arquivo `backend/.env`, preencha:

```env
ADMIN_EMAILS=seu-email@empresa.com,outro-admin@empresa.com
```

Quem se cadastrar usando um desses e-mails será criado como administrador. Os demais usuários serão comuns.

### 3. Frontend

Em outro terminal:

```bash
cd frontend
npm install
npm run dev
```

Acesse:

```text
http://localhost:5173
```

## Configuração do e-mail mensal

No arquivo `backend/.env`, altere:

```env
SMTP_HOST=smtp.gmail.com
SMTP_PORT=465
SMTP_SECURE=true
SMTP_USER=seu-email@gmail.com
SMTP_PASS=sua-senha-de-app
REPORT_TO=destinatario@empresa.com
REPORT_FROM="Sistema de Reembolso" <seu-email@gmail.com>
```

Para Gmail, crie uma senha de app na conta Google e use essa senha no `SMTP_PASS`.

O agendamento está em `backend/src/server.js`:

```js
cron.schedule('0 8 1 * *', ...)
```

Isso significa: todo dia 1º, às 08h, no fuso de São Paulo.

## Observações para produção

Este é um MVP funcional para rodar rápido. Para uso definitivo, recomendo trocar SQLite por PostgreSQL, subir comprovantes em um storage próprio, como S3, Cloudinary ou Supabase Storage, e publicar o frontend no Vercel e o backend no Render/Railway.

## Exportação por e-mail

Na tela **Filtros e relatório**, escolha o mês, status e categoria desejados, preencha o campo **E-mail para exportar relatório** e clique em **Exportar e enviar por e-mail**.

O sistema envia o relatório em HTML no corpo do e-mail e anexa também um CSV para abrir no Excel. Usuário comum exporta apenas os próprios lançamentos. Administrador exporta todos os lançamentos filtrados.
