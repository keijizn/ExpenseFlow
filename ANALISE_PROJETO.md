# Análise técnica do ExpenseFlow

Data: 6 de outubro de 2026.

> Este documento registra a análise anterior às correções. Para a implementação, testes e limitações atuais, consulte [ATUALIZACAO.md](ATUALIZACAO.md).

## Escopo e limites

Revisão do código local: frontend React/Vite, CSS, API Spring Boot, entidades, DTOs, repositórios, serviços, configuração, Docker e documentação. Nenhuma correção funcional foi aplicada. Os achados abaixo resultam da leitura dos fluxos; os cenários de backend não foram executados contra banco de dados. Não foram acessados dados nem endpoints da aplicação em produção.

Não há testes automatizados, configuração de CI ou Maven Wrapper nos arquivos versionados. Java e Node estão disponíveis; Maven e Docker não foram encontrados no PATH. Por isso, a compilação e os testes do backend não foram validados. A validação do frontend está registrada ao final.

## Avaliação geral

O projeto cobre um conjunto amplo de funcionalidades de finanças pessoais e apresenta uma separação compreensível entre controllers, services e repositories. BigDecimal no backend, BCrypt para senhas e buscas por proprietário em muitos endpoints são boas escolhas. A aplicação ainda precisa de correções de autenticação, autorização e consistência financeira antes de ser considerada pronta para uso com dados reais.

## Achados prioritários

### 1. Crítico — verificação de e-mail permite autenticação sem comprovar identidade

Referência: `backend/src/main/java/com/finanzero/service/AuthService.java:57`.

`verify()` chama `issueSession()` imediatamente quando o usuário já está verificado. A comparação do código só acontece depois desse retorno. Assim, uma requisição a `/api/auth/verify` com o e-mail de uma conta verificada e qualquer código não vazio recebe um token válido. A validação do DTO não impede esse caminho. Além de permitir acesso à conta, a emissão substitui o token anterior.

Correção: para contas já verificadas, retornar uma mensagem sem token e exigir o login normal. Testar que um código arbitrário ou antigo nunca emite sessão.

### 2. Crítico — POSTs aceitam IDs que podem sobrescrever registros de outros usuários

Referências: `controller/WalletAccountController.java:26`, `controller/CategoryController.java:30`, `controller/GoalController.java:24` e `service/DebtService.java:33`, sob `backend/src/main/java/com/finanzero/`.

Esses endpoints recebem a entidade JPA inteira, incluindo seu `id`, atribuem o usuário autenticado como proprietário e chamam `save()`. Não rejeitam nem removem IDs fornecidos pelo cliente. Um ID existente faz o Spring Data usar merge; a criação pode, portanto, atualizar um registro existente e trocar seu proprietário, sem passar pelas verificações presentes no PUT.

A conclusão decorre do código e da semântica documentada de persistência, não de um ataque executado: [Spring Data JPA — Persisting Entities](https://docs.spring.io/spring-data/jpa/reference/jpa/entity-persistence.html).

Correção: DTOs de criação sem ID e construção de uma entidade nova no servidor. Testar POSTs com IDs próprios e de outro usuário, incluindo contas, categorias, metas e dívidas.

### 3. Alto — comprovantes podem ser acessados sem autenticação

Referência: `backend/src/main/java/com/finanzero/controller/ReceiptController.java:21`.

A rota `/api/receipts/{fileName}` assina uma URL S3 sem consultar o usuário ou o dono do comprovante. Quem obtiver o nome UUID do arquivo pode renovar o acesso enquanto o objeto existir. O nome aleatório dificulta adivinhação, mas o link da aplicação não expira junto com a URL S3 gerada.

Correção: verificar a propriedade da transação para acesso pelo aplicativo. Para compartilhamento externo, manter uma URL S3 temporária ou um token de compartilhamento com escopo e expiração; não disponibilizar renovação pública irrestrita. Os links atuais do frontend precisam ser adaptados para o fluxo autenticado.

### 4. Alto — reembolso recebido pode gerar receita mais de uma vez

Referências: `backend/src/main/java/com/finanzero/service/ReimbursementService.java:55`, `:106`, `:142`; `service/TransactionService.java:88`.

`markReceived()` evita repetição apenas enquanto o status atual for REIMBURSED. O envio individual muda qualquer reembolso para SENT, a recusa muda para REJECTED e o PUT da transação aceita o status enviado pelo cliente. Depois de reabrir o estado por um desses caminhos, receber novamente cria outra receita. A receita gerada não possui uma relação persistente e única com o gasto original, apenas uma observação textual.

Correção: transições de estado controladas no backend, vínculo único entre reembolso e receita e operação idempotente. Incluir teste de receber → reenviar/editar/recusar → receber, além de requisições concorrentes.

### 5. Alto — alterações concorrentes podem perder atualizações de saldo

Referências: `backend/src/main/java/com/finanzero/service/TransactionService.java:149`, `service/InvestmentService.java:26`, `service/DebtService.java:60`, `model/WalletAccount.java`.

Os serviços leem o saldo, calculam um novo valor em memória e salvam. Não há `@Version`, bloqueio explícito ou atualização atômica nos repositórios. Duas operações que leiam R$ 100 e descontem R$ 10 e R$ 20 podem terminar em R$ 90 ou R$ 80 em vez de R$ 70. `@Transactional` sozinho não resolve esse conflito. O recebimento simultâneo do mesmo reembolso também não possui proteção suficiente.

Correção: definir uma estratégia consistente de concorrência, incluindo contas, dívidas e reembolsos; testar operações simultâneas com banco real de teste.

### 6. Alto — valores negativos produzem movimentos financeiros invertidos

Referências: `backend/src/main/java/com/finanzero/dto/TransactionRequest.java:19`, `service/InvestmentService.java:29`, `service/DebtService.java:63`.

Transações exigem valor não nulo, mas não positivo. Investimentos não têm validação de valor no DTO/controller. Uma despesa ou investimento de -100 aumenta o saldo. O formulário de criação de transações também não tem `min`. Dívidas aceitam parcela mensal negativa; o fallback do pagamento pode usar esse valor e aumentar o saldo enquanto reduz o total pago.

Correção: validar valores, escala monetária, limites e combinações de tipo/status no servidor; definir explicitamente os campos que podem ser zero. Não impedir saldos negativos de contas por engano: saldo e valor de operação têm regras diferentes.

### 7. Alto — falha no upload pode levar à duplicação do lançamento

Referência: `frontend/src/main.jsx:532`.

O formulário cria a transação e depois envia o comprovante. Se o segundo passo falhar, a transação já está salva e o formulário permanece aberto sem tratamento do erro. Um novo clique em Salvar cria outra transação e movimenta o saldo novamente. O botão também não bloqueia submissões simultâneas.

Correção: manter o ID criado, permitir repetir apenas o upload, mostrar o sucesso parcial e bloquear envios enquanto houver uma operação em andamento. Considerar chave de idempotência na criação.

### 8. Alto — sessões não expiram nem são revogadas ao sair

Referências: `backend/src/main/java/com/finanzero/service/CurrentUserService.java:15`, `service/AuthService.java:140`; `frontend/src/main.jsx:411`.

O token efetivo fica em `AppUser.authToken`; a entidade `AuthToken`, que possui expiração, não participa do fluxo. A autenticação verifica somente token e conta verificada. Sair apaga o armazenamento do navegador, mas não invalida o token no backend. Um token copiado continua utilizável até ser substituído por outro login ou apagado na recuperação de senha.

Correção: implementar expiração, revogação no logout e política de sessões. Revisar armazenamento do token no navegador como parte desse desenho.

### 9. Alto — recuperação e verificação sem limitação de tentativas no código

Referências: `backend/src/main/java/com/finanzero/service/AuthService.java:94`, `:178`; `controller/AuthController.java`.

Os códigos têm seis dígitos, usam `java.util.Random` e não há contador de falhas ou limitação de requisições na aplicação. Isso permite tentativas repetidas e abuso do envio de e-mails. Não foi verificado se a infraestrutura externa oferece algum controle adicional.

Correção: usar geração criptográfica, limite de tentativas por desafio, intervalo para reenvio e limitação por conta/IP. O cadastro também precisa de uma política de senha coerente com a redefinição.

### 10. Médio — pagamento de dívida não atualiza o próximo vencimento nem gera histórico

Referência: `backend/src/main/java/com/finanzero/service/DebtService.java:60`.

Pagar uma parcela altera saldo e valor pago, mas mantém `nextDueDate`. Uma dívida com parcela vencida permanece atrasada após pagá-la, mesmo quando deveria avançar para o próximo mês. Não existe registro individual do pagamento com data e conta; dashboard, histórico e CSV consultam transações e não incluem esses pagamentos. Portanto, suas saídas não conciliam todas as movimentações do saldo.

Correção: definir a regra de parcelas e pagamentos parciais, registrar cada pagamento e integrá-lo ao histórico, evitando contar a dívida e o pagamento duas vezes.

### 11. Médio — controle de cartão não fecha o ciclo de pagamento da fatura

Referências: `frontend/src/main.jsx:707`; `backend/src/main/java/com/finanzero/service/TransactionService.java:169`.

Compras no crédito reduzem o limite, mas não há operação de pagar a fatura, debitar a conta e recompor o limite. A tela soma compras por mês de calendário, sem fechamento/vencimento. Também inclui despesas fixas pendentes no total, embora o backend só reduza o limite dessas despesas quando PAID.

Correção: definir se a funcionalidade será apenas um relatório de compras ou um controle completo de faturas. Para o segundo caso, modelar fatura, vencimento, liquidação e limite total/disponível separadamente.

### 12. Médio — limite de categoria aparece zerado nos relatórios

Referências: `backend/src/main/java/com/finanzero/dto/CategoryUsage.java:5`; `frontend/src/main.jsx:763`.

A API devolve `limitValue`, mas a tela lê `monthlyLimit`. O fallback exibe R$ 0,00 mesmo com limite cadastrado; a porcentagem pode aparecer correta ao lado do limite errado.

Correção: alinhar o contrato e adicionar um teste com limite não zero.

### 13. Médio — dados da sessão anterior permanecem em memória

Referência: `frontend/src/main.jsx:377` e `:411`.

Logout não limpa `state`; as requisições antigas também não são canceladas. Se outra pessoa entrar no mesmo navegador e a carga falhar, o estado anterior volta a ser renderizado quando `loading` fica falso. Respostas atrasadas da sessão anterior também podem sobrescrever o estado atual.

Correção: zerar os dados ao sair/trocar de usuário e descartar ou cancelar respostas de sessões e períodos anteriores. Tratar sessão inválida centralmente na camada de API.

### 14. Médio — datas padrão seguem UTC, não o dia local

Referência: `frontend/src/main.jsx:329` e `:528`.

`new Date().toISOString().slice(0, 10)` usa UTC. Em São Paulo, após 21h, o formulário e o recebimento de reembolso podem usar o dia seguinte. Na virada do mês isso altera o período do relatório.

Correção: construir a data civil pelos componentes locais e definir o fuso de negócio do backend.

### 15. Médio — falhas de exclusão e gravação não têm tratamento útil

Referências: controllers de contas/categorias, `controller/ApiExceptionHandler.java` e funções de edição/exclusão em `frontend/src/main.jsx`.

Contas e categorias referenciadas são excluídas diretamente. As relações JPA podem impedir a exclusão por chave estrangeira; não há tratamento específico para explicar o conflito. A maior parte das mutações do frontend não captura erros. O usuário pode clicar e não receber informação útil, especialmente em exclusões de registros usados.

Correção: definir arquivamento ou bloqueio de exclusão, retornar erro de conflito compreensível e exibi-lo no formulário. Evitar cascata que apague o histórico financeiro.

## Desempenho, manutenção e operação

- O dashboard executa 24 consultas mensais para os gráficos anuais, além das consultas de resumo. Agregar por mês no banco reduz esse trabalho.
- Cada recarga do frontend busca oito recursos, incluindo todas as transações e todos os reembolsos. Não há paginação. A troca de período refaz até consultas que não dependem do período.
- `TransactionService.list()` ignora `type` se as duas datas não vierem juntas; a API aceita filtros que não aplica em todos os casos.
- `DebtService.list()` salva cada dívida durante um GET; a listagem de transações também pode persistir atrasos. Leituras têm efeitos colaterais e custo adicional.
- O frontend está concentrado em `main.jsx` (cerca de 780 linhas), com telas, autenticação, estado, formulários e exportação. Separar por domínio melhora a manutenção; centralizar contratos evita divergências como `limitValue`.
- Há suporte a três temas e a movimento reduzido. Entretanto, várias tabelas não usam o wrapper de rolagem `.tableWrap`, e o CSS dá largura mínima às células em telas pequenas. A responsividade dessas telas precisa de validação visual. Inputs dependem frequentemente de placeholders e botões de exclusão não têm nome acessível.
- Não há testes automatizados, lint ou CI no repositório. Prioridade dos testes: autenticação, isolamento entre usuários, concorrência, saldo e reembolsos.
- `ddl-auto=update` substitui migrações versionadas. Adotar migrações e validar esquema em produção permite revisar e reproduzir mudanças.
- O seeder cria uma conta demo com senha conhecida quando o banco está vazio, sem restrição de perfil. Restringi-lo a desenvolvimento/demo; essa conta não deve ser usada para dados pessoais.
- EmailJS é chamado dentro de transações de banco e pode levar até o timeout de 20 segundos. E-mail enviado não é desfeito se o commit falhar. Avaliar fila/outbox para envio confiável.
- Exclusão de comprovantes ocorre antes de concluir alterações no banco. Falhas posteriores podem deixar metadados apontando para arquivo removido; `deleteQuietly` também ignora falhas e pode deixar objetos órfãos. Definir compensação e limpeza posterior.
- A validação de upload confia no Content-Type declarado pelo cliente. Validar o conteúdo real além do tamanho e extensão.
- O tratamento de autenticação retorna 400, não distingue 401/403 e não direciona o frontend para refazer login.
- Dependências do frontend estão declaradas como `latest`, embora o lockfile fixe as versões instaladas. Preservar o lockfile, usar instalação reprodutível e tornar atualizações deliberadas. Não foi realizada auditoria de CVEs nesta revisão.
- README mistura versões, menciona um `vite.config.js` ausente e contém instruções locais parcialmente divergentes. `app.mail.enabled=true` exige configuração do EmailJS para cadastrar usuários; o comentário de modo simulado pode induzir a erro.

## Ordem recomendada

1. Corrigir autenticação por verificação de e-mail e POSTs que aceitam IDs; adicionar regressões com dois usuários.
2. Proteger comprovantes, definir sessões e limitar tentativas de autenticação/recuperação.
3. Corrigir valores financeiros, concorrência e idempotência de reembolsos/criação.
4. Completar pagamentos de dívidas/cartões e conciliar histórico, saldo e relatórios.
5. Corrigir contratos do frontend, isolamento de sessão, datas, mensagens de erro e acessibilidade.
6. Introduzir migrações, testes/CI, paginação e consultas agregadas.

## Validação

- Leitura de todos os arquivos-fonte, configuração e documentação versionados.
- Dependências do frontend instaladas com `npm ci --ignore-scripts --no-audit --no-fund`; lockfile preservado.
- `npm run build`: passou com Vite 8.1.3. Bundle JavaScript de 596,75 kB (174,91 kB gzip), com alerta de chunk acima de 500 kB. Dividir telas com carregamento sob demanda pode reduzir o carregamento inicial.
- Backend: revisão estática; compilação e cenários integrados não executados por ausência de Maven/Docker no PATH e ambiente de banco configurado para testes.
- Produção, serviços AWS/EmailJS e comportamento visual no navegador não foram validados.
