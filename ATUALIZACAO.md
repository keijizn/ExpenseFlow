# Correções e aplicativo móvel

Implementação local em 6 de outubro de 2026. Nenhuma alteração foi publicada no Render/Vercel nem aplicada ao banco de produção.

## O que mudou

- Verificar uma conta já verificada não emite sessão. Login continua exigindo senha.
- Tokens são armazenados como hash no banco, expiram em 24 horas e são revogados no logout. No navegador, a sessão fica em sessionStorage, não em armazenamento persistente. Fechar a sessão do navegador pode exigir novo login.
- Códigos usam SecureRandom. Há limites por conta e IP e intervalo mínimo para reenviar e-mails. O limitador é por instância e reinicia com o processo; ao escalar para várias instâncias, usar um limitador compartilhado/gateway. Cabeçalhos de proxy não são confiados automaticamente para obter IP.
- POSTs de contas, categorias, metas e dívidas rejeitam IDs fornecidos pelo cliente.
- Comprovantes exigem autenticação e propriedade. A rota retorna JSON com URL temporária; links antigos diretos à rota não são mais públicos. E-mails continuam recebendo URLs S3 temporárias.
- Movimentações financeiras são serializadas por usuário com bloqueio de linha no banco. Reembolsos têm vínculo único com a receita, estados finais protegidos e recebimento idempotente.
- Valores de operações são validados; saldos bancários e limites disponíveis podem continuar negativos para representar saldo devedor/limite excedido. Lançamentos aceitam Idempotency-Key para repetir uma criação sem duplicar o débito; reutilizar a chave com outros dados retorna conflito.
- Falhar no upload preserva o lançamento criado: o formulário permite reenviar apenas o comprovante ou concluir sem ele. A exclusão S3 fica para depois do commit; rollback do upload tenta remover o novo objeto.
- Pagamentos de dívida geram histórico. Pagamentos parciais acumulam até completar a parcela e avançar o vencimento. Dívidas com histórico e lançamentos gerados por pagamentos não podem ser excluídos por CRUD genérico.
- A tela de cartões permite liquidar compras ainda não liquidadas de um mês, debitar a conta escolhida e restaurar o limite. A liquidação aparece no histórico, mas não duplica a despesa nos indicadores.
- Categorias exibem o limite correto; datas padrão usam o dia local; logout limpa dados e invalida respostas antigas; erros aparecem na interface; tabelas têm rolagem própria no celular.
- Gráficos carregam sob demanda. O bundle inicial passou de aproximadamente 597 kB para 248 kB antes de gzip; gráficos usam um chunk separado de aproximadamente 357 kB.
- Banco com migrações Flyway e validação de esquema, conta demo apenas no perfil demo, dependências frontend fixadas e workflow de CI.

## Instalar no Android e iPhone

1. Publicar frontend e API com HTTPS e configurar VITE_API_URL e APP_CORS_ALLOWED_ORIGIN para os endereços corretos.
2. Android: abrir no Chrome e tocar em Instalar aplicativo. Caso o convite não esteja disponível, usar o menu do navegador.
3. iPhone: abrir no Safari → Compartilhar → Adicionar à Tela de Início.
4. Usar o botão Nova despesa. Navegadores compatíveis também oferecem esse atalho no ícone instalado.

O aplicativo possui manifesto, ícones, modo standalone, mensagem de conexão, aviso de atualização e página offline. Dados financeiros, tokens, comprovantes e respostas da API não entram no cache do service worker. Consultas e gravações exigem internet; não há fila de lançamentos offline. Instalar não equivale a publicar nas lojas.

Em desenvolvimento, usar `npm run build` e `npm run preview` para validar o service worker; ele não é registrado no servidor de desenvolvimento Vite. A API deve aceitar a origem do preview (por exemplo, http://127.0.0.1:4173).

## Atualizar banco existente

O backend agora usa `spring.jpa.hibernate.ddl-auto=validate`. Para uma base vazia, Flyway aplica V1 e V2 automaticamente. Para a base antiga criada pelo Hibernate:

1. Fazer backup e testar primeiro numa cópia PostgreSQL da base. Conferir que o esquema corresponde à versão anterior do projeto e que as colunas de V2 ainda não existem.
2. Na primeira inicialização dessa base, definir temporariamente `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` e `SPRING_FLYWAY_BASELINE_VERSION=1`. Isso registra o esquema antigo como V1 e aplica V2 e V3.
3. Confirmar a aplicação de V2 e V3 na tabela flyway_schema_history e a inicialização do backend com validação do esquema.
4. Remover `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` depois da atualização. Não usar baseline para esconder uma incompatibilidade de esquema.
5. Todos os usuários precisam fazer login novamente: V2 invalida tokens antigos deliberadamente.

As migrações preservam os valores existentes. Não reconstroem pagamentos antigos nem corrigem automaticamente saldos já inconsistentes. Reembolsos antigos concluídos permanecem bloqueados para reabertura; receitas antigas sem vínculo precisam de revisão caso tenham sido duplicadas no passado. Compras anteriores aparecem como não liquidadas, pois o modelo antigo não registrava liquidação: conciliar compras históricas antes de usar o novo pagamento de fatura sobre períodos antigos.

Em 7 de outubro de 2026, a atualização foi aplicada à branch Neon `teste-atualizacao`, criada pelo usuário a partir de `production`. Nenhuma conexão ou migração foi executada contra a branch de produção.

A inspeção encontrou uma diferença do esquema implantado: `debt.account_id` não existia. A migração V3 adiciona essa associação opcional quando ausente, sem atribuir uma conta às dívidas antigas. O teste de migração local também passou a cobrir essa condição.

Validação na cópia Neon (PostgreSQL 18.6): baseline V1, aplicação de V2/V3, validação Flyway e inicialização completa do backend local com `ddl-auto=validate` e baseline desativado. A comparação por hashes de todas as colunas preexistentes nas oito tabelas confirmou preservação dos registros, exceto `app_user.auth_token`, cuja invalidação prevista foi verificada separadamente. E-mail e bucket S3 foram desativados no processo local. A primeira tentativa do verificador sem servidor web falhou por ausência de contexto HTTP; a execução em servidor local temporário concluiu com sucesso.

O Flyway 10.10.0 incluído pelo Spring Boot atual emitiu aviso de que PostgreSQL 18.6 é mais recente que sua faixa testada. As migrações e a validação passaram nesta cópia; a atualização das dependências para suporte oficialmente testado continua pendente. O backup de produção e a publicação no Render/Vercel ainda não foram realizados. A branch de teste expira em 14 de outubro de 2026.

## Testes

- Backend: `mvn -f backend/pom.xml verify` — 18 testes passando, zero falhas e JAR gerado: autenticação, isolamento, comprovantes, valores, idempotência, concorrência, sessões, dívidas, cartões, exclusão, filtros e migração.
- Frontend: `cd frontend`, `npm ci`, `npm test`, `npm run build` — 8 testes passando e build aprovado.
- CI: `.github/workflows/ci.yml` roda Java 17 e Node 22. Os testes locais usaram Java 21 compilando para Java 17 e Node 24.
- Interface conferida no navegador local em 390 × 844: login na conta demo, instruções de instalação e criação de despesa fictícia de R$ 12,34, com retorno ao histórico. Captura local em `.tools/mobile-validation.jpg`. Instalação em aparelhos físicos Android/iPhone, entrega de e-mails e S3 reais não foram exercitados.

## Escopo das faturas e próximos passos

A liquidação atual agrupa compras pelo mês de calendário e paga o total ainda aberto, sem juros, pagamento parcial, fechamento em dia personalizado ou parcelamento de compra. A tela informa essa regra. Compras fixas pendentes não entram na fatura até serem marcadas como pagas.

Por sua escolha, a captura automática de compras ficou para uma etapa posterior:

- **PWA:** instala o site como aplicativo, mas não intercepta pagamentos NFC. [Instalação de PWAs](https://developer.mozilla.org/en-US/docs/Web/Progressive_web_apps/Guides/Making_PWAs_installable).
- **iPhone/Atalhos:** a Apple oferece um gatilho de transação da Carteira. Uma futura integração pode encaminhar os dados disponibilizados pelo aparelho a uma API restrita e idempotente; precisa de configuração e teste no cartão/dispositivo. [Gatilhos de transação](https://support.apple.com/en-euro/guide/shortcuts/apd65c67538a/ios).
- **Android e iPhone:** a alternativa comum é sincronização de dados bancários via um provedor integrado ao Open Finance, com consentimento e condições próprias de atualização/custo. Não é garantia de registro instantâneo no momento da aproximação. [Open Finance — Banco Central](https://www.bcb.gov.br/estabilidadefinanceira/cliente-open-finance).

O projeto ainda busca listas completas, e o envio de e-mail continua síncrono. Paginação, divisão adicional de telas e fila/outbox são melhorias futuras de escala; não foram apresentadas como concluídas. A exclusão de S3 após commit registra falhas para limpeza posterior, mas não tem uma fila durável de repetição.
