import React, { useEffect, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { BarChart, Bar, PieChart, Pie, Cell, ResponsiveContainer, Tooltip, LineChart, Line } from 'recharts';
import { LayoutDashboard, TrendingUp, TrendingDown, WalletCards, Target, PiggyBank, Tags, Plus, Trash2, RefreshCw, CreditCard, Mail, LogOut, ReceiptText, Sun, Moon } from 'lucide-react';
import { api } from './services/api.js';
import './styles.css';

const BRL = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const today = new Date();
const month = today.getMonth() + 1;
const year = today.getFullYear();

const menu = [
  ['Dashboard', LayoutDashboard],
  ['Ganhos', TrendingUp],
  ['Despesas Fixas', WalletCards],
  ['Despesas Variáveis', TrendingDown],
  ['Dívidas', WalletCards],
  ['Economias', PiggyBank],
  ['Metas', Target],
  ['Categorias', Tags],
  ['Relatórios', LayoutDashboard],
  ['Cartões/Faturas', ReceiptText],
  ['Reembolsos', Mail],
  ['Contas Bancárias', CreditCard]
];

const goalIcons = ['🎯', '💰', '🏦', '🚗', '🏠', '✈️', '📱', '💻', '🎮', '📚', '🐶', '🎁', '💍', '🛠️', '🧳', '🏖️', '🚀', '⭐'];
const categoryIcons = ['💰', '💵', '🏦', '🧾', '🏠', '⚡', '💧', '📶', '🛒', '🍔', '🍕', '☕', '🚗', '⛽', '🚌', '🚕', '💊', '🏥', '🎓', '📚', '🎮', '🎬', '🎁', '🐶', '🐱', '✈️', '🏖️', '💻', '📱', '🛠️', '🧰', '👕', '💳', '📦', '⭐'];
const paymentMethods = ['Pix', 'Débito', 'Crédito'];
const reimbursementStatuses = { TO_SEND: 'A enviar', SENT: 'Enviado', REIMBURSED: 'Reembolsado', REJECTED: 'Recusado', NOT_REIMBURSABLE: 'Não reembolsável' };
const API_BASE = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';
function receiptUrl(fileName) { return `${API_BASE}/receipts/${fileName}`; }


function ThemeToggle({ theme, setTheme }) {
  const isDark = theme === 'dark';
  return <button type="button" className="themeToggle" onClick={() => setTheme(isDark ? 'light' : 'dark')} title={isDark ? 'Ativar tema claro' : 'Ativar tema escuro'}>
    {isDark ? <Sun size={16} /> : <Moon size={16} />}
    <span>{isDark ? 'Claro' : 'Escuro'}</span>
  </button>;
}

function progress(value, total) {
  const v = Number(value || 0);
  const t = Number(total || 0);
  return t > 0 ? Math.min(100, Math.round((v / t) * 100)) : 0;
}

function remainingDebt(debt) {
  return Math.max(0, Number(debt.totalAmount || 0) - Number(debt.paidAmount || 0));
}

function formatDate(date) {
  return date ? new Date(date + 'T00:00').toLocaleDateString('pt-BR') : '-';
}

function isPastDate(date) {
  if (!date) return false;
  const currentDate = new Date();
  currentDate.setHours(0, 0, 0, 0);
  const itemDate = new Date(date + 'T00:00');
  itemDate.setHours(0, 0, 0, 0);
  return itemDate < currentDate;
}

function getDisplayStatus(transaction) {
  if (transaction.type === 'FIXED_EXPENSE' && transaction.status === 'PENDING' && isPastDate(transaction.dueDate || transaction.date)) {
    return 'OVERDUE';
  }
  return transaction.status;
}

function getDebtStatus(debt) {
  if (Number(debt.paidAmount || 0) >= Number(debt.totalAmount || 0) && Number(debt.totalAmount || 0) > 0) return 'PAID';
  if (debt.nextDueDate && isPastDate(debt.nextDueDate)) return 'OVERDUE';
  return debt.status || 'PENDING';
}

function isInPeriod(date, period) {
  if (!date) return false;
  const d = new Date(date + 'T00:00');
  return d.getMonth() + 1 === Number(period.month) && d.getFullYear() === Number(period.year);
}

function categoryTypeLabel(type) {
  return {
    INCOME: 'Ganho',
    FIXED_EXPENSE: 'Despesa fixa',
    VARIABLE_EXPENSE: 'Despesa variável'
  }[type] || type;
}

function buildTransactionPayload(transaction, newStatus) {
  return {
    description: transaction.description,
    type: transaction.type,
    amount: Number(transaction.amount || 0),
    date: transaction.date,
    dueDate: transaction.type === 'FIXED_EXPENSE' ? (transaction.dueDate || transaction.date) : transaction.dueDate,
    receivedAt: transaction.receivedAt,
    paymentMethod: transaction.paymentMethod || 'Pix',
    notes: transaction.notes,
    reimbursable: !!transaction.reimbursable,
    reimbursementCompany: transaction.reimbursementCompany,
    reimbursementEmail: transaction.reimbursementEmail,
    reimbursementStatus: transaction.reimbursementStatus,
    status: newStatus,
    categoryId: transaction.category?.id || null,
    accountId: transaction.account?.id || null
  };
}


function AuthScreen({ onAuth, theme, setTheme }) {
  const [mode, setMode] = useState('login');
  const [form, setForm] = useState({ name: '', email: '', password: '', code: '', newPassword: '' });
  const [message, setMessage] = useState('');
  const [debugCode, setDebugCode] = useState('');
  const [error, setError] = useState('');

  function switchMode(nextMode) {
    setMode(nextMode);
    setError('');
    setMessage('');
    setDebugCode('');
  }

  async function submit(e) {
    e.preventDefault();
    setError('');
    setMessage('');
    try {
      if (mode === 'register') {
        const res = await api.post('/auth/register', { name: form.name, email: form.email, password: form.password });
        setMessage(res.message || 'Cadastro criado. Verifique seu e-mail.');
        setDebugCode(res.debugVerificationCode || '');
        setMode('verify');
        return;
      }
      if (mode === 'verify') {
        const res = await api.post('/auth/verify', { email: form.email, code: form.code });
        if (res.token) onAuth(res);
        return;
      }
      if (mode === 'forgot') {
        const res = await api.post('/auth/forgot-password', { email: form.email });
        setMessage(res.message || 'Enviamos um código para redefinir sua senha.');
        setMode('reset');
        return;
      }
      if (mode === 'reset') {
        const res = await api.post('/auth/reset-password', { email: form.email, code: form.code, newPassword: form.newPassword });
        setMessage(res.message || 'Senha redefinida. Faça login novamente.');
        setForm({ ...form, password: '', code: '', newPassword: '' });
        setMode('login');
        return;
      }
      const res = await api.post('/auth/login', { email: form.email, password: form.password });
      if (res.token) onAuth(res);
      else {
        setMessage(res.message || 'Verifique seu e-mail.');
        setDebugCode(res.debugVerificationCode || '');
        setMode('verify');
      }
    } catch (e) {
      setError(e.message || 'Não foi possível autenticar.');
    }
  }

  async function resendCode() {
    setError('');
    try {
      const res = await api.post('/auth/resend', { email: form.email });
      setMessage(res.message || 'Novo código enviado.');
      setDebugCode(res.debugVerificationCode || '');
    } catch (e) {
      setError(e.message || 'Não foi possível reenviar o código.');
    }
  }

  const title = {
    login: 'Entrar',
    register: 'Criar conta',
    verify: 'Verificar e-mail',
    forgot: 'Recuperar senha',
    reset: 'Redefinir senha'
  }[mode];

  return <div className="authPage">
    <div className="authTheme"><ThemeToggle theme={theme} setTheme={setTheme} /></div>
    <section className="authCard">
      <div className="brand authBrand"><div className="logo">〽</div><div><strong>FinanZero</strong><span>Seu controle financeiro pessoal</span></div></div>
      <h1>{title}</h1>
      <p className="muted">Use uma conta pessoal. O sistema não cria área ADMIN porque, por enquanto, cada usuário gerencia apenas os próprios dados.</p>
      {error && <div className="alert">{error}</div>}
      {message && <div className="successBox">{message}{debugCode && <><br /><b>Código para teste local: {debugCode}</b></>}</div>}
      <form className="authForm" onSubmit={submit}>
        {mode === 'register' && <input placeholder="Nome" value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} required />}
        <input type="email" placeholder="E-mail" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} required />
        {(mode === 'login' || mode === 'register') && <input type="password" placeholder="Senha" value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} required />}
        {(mode === 'verify' || mode === 'reset') && <input placeholder="Código recebido por e-mail" value={form.code} onChange={e => setForm({ ...form, code: e.target.value })} required />}
        {mode === 'reset' && <input type="password" placeholder="Nova senha" value={form.newPassword} onChange={e => setForm({ ...form, newPassword: e.target.value })} required />}
        <button>{mode === 'login' ? 'Entrar' : mode === 'register' ? 'Cadastrar' : mode === 'verify' ? 'Verificar' : mode === 'forgot' ? 'Enviar código' : 'Redefinir senha'}</button>
      </form>
      <div className="authLinks">
        {mode !== 'login' && <button className="ghost" onClick={() => switchMode('login')}>Já tenho conta</button>}
        {mode !== 'register' && <button className="ghost" onClick={() => switchMode('register')}>Criar conta</button>}
        {mode === 'verify' && <button className="ghost" onClick={resendCode}>Reenviar código</button>}
        {mode === 'login' && <button className="ghost" onClick={() => switchMode('forgot')}>Esqueci minha senha</button>}
        {mode === 'forgot' && <button className="ghost" onClick={() => switchMode('reset')}>Já tenho o código</button>}
      </div>
      <p className="demoHint">Conta demo: <b>demo@finanzero.local</b> / <b>123456</b></p>
    </section>
  </div>;
}

function ReimbursementScreen({ items, accounts, reload }) {
  const [selectedIds, setSelectedIds] = useState([]);
  const [batchEmail, setBatchEmail] = useState('');
  const [batchCompany, setBatchCompany] = useState('');
  const openItems = items.filter(i => i.reimbursementStatus !== 'REIMBURSED' && i.reimbursementStatus !== 'REJECTED');
  const totalOpen = openItems.reduce((sum, i) => sum + Number(i.amount || 0), 0);
  const selectedItems = items.filter(i => selectedIds.includes(i.id));
  const selectedTotal = selectedItems.reduce((sum, i) => sum + Number(i.amount || 0), 0);
  const allOpenSelected = openItems.length > 0 && openItems.every(i => selectedIds.includes(i.id));

  function toggleItem(id) {
    setSelectedIds(prev => prev.includes(id) ? prev.filter(itemId => itemId !== id) : [...prev, id]);
  }

  function toggleAllOpen() {
    setSelectedIds(allOpenSelected ? [] : openItems.map(i => i.id));
  }

  async function sendSelected() {
    if (selectedIds.length === 0) return alert('Selecione ao menos um reembolso.');

    const email = (batchEmail || selectedItems[0]?.reimbursementEmail || prompt('E-mail para enviar os reembolsos:') || '').trim();
    if (!email) return alert('Informe o e-mail para envio.');

    const company = (batchCompany || selectedItems[0]?.reimbursementCompany || prompt('Empresa responsável pelo reembolso:', 'Empresa') || 'Empresa').trim();

    try {
      await api.post('/reimbursements/send-batch', {
        transactionIds: selectedIds,
        email,
        company
      });
      alert(`${selectedIds.length} reembolso(s) enviado(s) por e-mail.`);
      setSelectedIds([]);
      setBatchEmail('');
      setBatchCompany('');
      reload();
    } catch (error) {
      console.error('Erro ao enviar reembolsos em lote:', error);
      alert(error.message || 'Erro ao enviar reembolsos.');
    }
  }

  return <section className="card page">
    <div className="sectionHead">
      <div><h2>Reembolsos</h2><p className="muted">Gastos feitos por você que podem ser enviados para empresa e depois lançados como reembolso recebido.</p></div>
      <strong>{BRL.format(totalOpen)} em aberto</strong>
    </div>

    {openItems.length > 0 && <div className="batchPanel">
      <div>
        <strong>{selectedIds.length} selecionado(s)</strong>
        <span className="muted"> • {BRL.format(selectedTotal)} para enviar</span>
      </div>
      <input placeholder="E-mail de destino" value={batchEmail} onChange={e => setBatchEmail(e.target.value)} />
      <input placeholder="Empresa" value={batchCompany} onChange={e => setBatchCompany(e.target.value)} />
      <button type="button" onClick={sendSelected}>Enviar selecionados</button>
    </div>}

    <table>
      <thead><tr><th><input type="checkbox" checked={allOpenSelected} onChange={toggleAllOpen} /></th><th>Gasto</th><th>Data</th><th>Empresa</th><th>E-mail</th><th>Valor</th><th>Comprovante</th><th>Status</th><th>Receber em</th><th>Ações</th></tr></thead>
      <tbody>{items.map(item => <ReimbursementRow key={item.id} item={item} accounts={accounts} reload={reload} selected={selectedIds.includes(item.id)} toggle={() => toggleItem(item.id)} />)}</tbody>
    </table>
    {items.length === 0 && <p className="muted emptyState">Nenhum gasto reembolsável cadastrado. Marque uma despesa como “Gasto reembolsável” ao criar o lançamento.</p>}
  </section>;
}

function ReimbursementRow({ item, accounts, reload, selected, toggle }) {
  const [accountId, setAccountId] = useState(item.account?.id || accounts[0]?.id || '');
  const closed = item.reimbursementStatus === 'REIMBURSED' || item.reimbursementStatus === 'REJECTED';

  async function send() {
    const email = (item.reimbursementEmail || prompt('E-mail para enviar o reembolso:') || '').trim();
    if (!email) {
      alert('Informe um e-mail para enviar o reembolso.');
      return;
    }

    try {
      await api.post(`/reimbursements/${item.id}/send`, {
        email,
        company: item.reimbursementCompany || 'Empresa'
      });
      alert('Solicitação de reembolso enviada por e-mail.');
      reload();
    } catch (error) {
      console.error('Erro ao enviar reembolso:', error);
      alert(error.message || 'Erro ao enviar reembolso.');
    }
  }

  async function markReceived() {
    if (!accountId) return alert('Selecione a conta em que o reembolso entrou.');
    await api.post(`/reimbursements/${item.id}/received`, { accountId: Number(accountId), receivedAt: new Date().toISOString().slice(0, 10) });
    reload();
  }

  async function reject() {
    if (!confirm('Marcar este reembolso como recusado?')) return;
    await api.post(`/reimbursements/${item.id}/reject`, {});
    reload();
  }

  return <tr>
    <td>{closed ? '-' : <input type="checkbox" checked={selected} onChange={toggle} />}</td>
    <td>{item.description}<br /><small>{item.category?.name || '-'} • {item.paymentMethod}</small></td>
    <td>{formatDate(item.date)}</td>
    <td>{item.reimbursementCompany || '-'}</td>
    <td>{item.reimbursementEmail || '-'}</td>
    <td><b>{BRL.format(item.amount || 0)}</b></td>
    <td>{item.receiptFileName ? <a className="receiptLink" href={receiptUrl(item.receiptFileName)} target="_blank" rel="noreferrer">Ver comprovante</a> : '-'}</td>
    <td><span className={`status ${item.reimbursementStatus}`}>{reimbursementStatuses[item.reimbursementStatus] || item.reimbursementStatus}</span></td>
    <td>{closed ? '-' : <select className="statusSelect" value={accountId} onChange={e => setAccountId(e.target.value)}><option value="">Conta</option>{accounts.map(a => <option value={a.id} key={a.id}>{a.name}</option>)}</select>}</td>
    <td><div className="stackedControls">
      {!closed && <button type="button" className="sendEmailButton" onClick={send}>Enviar e-mail</button>}
      {!closed && <button type="button" className="ghost" onClick={markReceived}>Marcar recebido</button>}
      {!closed && <button type="button" className="ghost" onClick={reject}>Recusar</button>}
      {closed && <span className="muted">Finalizado</span>}
    </div></td>
  </tr>;
}

function App({ theme, setTheme }) {
  const [session, setSession] = useState(() => {
    const token = localStorage.getItem('finanzero_token');
    const user = localStorage.getItem('finanzero_user');
    if (!token || !user) return null;
    try {
      return JSON.parse(user);
    } catch {
      localStorage.removeItem('finanzero_user');
      localStorage.removeItem('finanzero_token');
      return null;
    }
  });
  const [screen, setScreen] = useState('Dashboard');
  const [state, setState] = useState({ summary: null, transactions: [], categories: [], accounts: [], debts: [], goals: [], investments: [], reimbursements: [] });
  const [period, setPeriod] = useState({ month, year });
  const [loading, setLoading] = useState(!!session);
  const [error, setError] = useState('');

  async function load() {
    setLoading(true);
    setError('');
    try {
      const [summary, transactions, categories, accounts, debts, goals, investments, reimbursements] = await Promise.all([
        api.get(`/dashboard?month=${period.month}&year=${period.year}`),
        api.get('/transactions'),
        api.get('/categories'),
        api.get('/accounts'),
        api.get('/debts'),
        api.get('/goals'),
        api.get('/investments'),
        api.get('/reimbursements')
      ]);
      setState({ summary, transactions, categories, accounts, debts, goals, investments, reimbursements });
    } catch (e) {
      setError(e.message || 'Não consegui conectar no backend. Confirme se o Spring está rodando na porta 8080.');
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (session) load();
  }, [session?.token, period.month, period.year]);

  function handleAuth(auth) {
    api.setAuthToken(auth.token);
    const user = { token: auth.token, name: auth.name, email: auth.email, role: auth.role };
    localStorage.setItem('finanzero_token', auth.token);
    localStorage.setItem('finanzero_user', JSON.stringify(user));
    setSession(user);
  }

  function logout() {
    api.setAuthToken('');
    localStorage.removeItem('finanzero_token');
    localStorage.removeItem('finanzero_user');
    setSession(null);
    setScreen('Dashboard');
  }

  if (!session) return <AuthScreen onAuth={handleAuth} theme={theme} setTheme={setTheme} />;

  const income = state.transactions.filter(t => t.type === 'INCOME');
  const fixed = state.transactions.filter(t => t.type === 'FIXED_EXPENSE');
  const variable = state.transactions.filter(t => t.type === 'VARIABLE_EXPENSE');

  return (
    <div className="app">
      <aside className="sidebar">
        <div className="brand"><div className="logo">〽</div><div><strong>FinanZero</strong><span>Organize. Planeje. Conquiste.</span></div></div>
        <nav>{menu.map(([label, Icon]) => <button key={label} onClick={() => setScreen(label)} className={screen === label ? 'active' : ''}><Icon size={17} />{label}</button>)}</nav>
        <div className="profile"><div className="avatar">{session.name?.[0] || 'U'}</div><div><strong>{session.name}</strong><span>{session.email}</span></div></div>
      </aside>
      <main>
        <header className="topbar"><div><p className="eyebrow">FinanZero</p><h1>{screen}</h1></div><div className="topActions"><ThemeToggle theme={theme} setTheme={setTheme} /><button className="ghost" onClick={load}><RefreshCw size={16} /> Atualizar</button><button className="ghost" onClick={logout}><LogOut size={16} /> Sair</button></div></header>
        {error && <div className="alert">{error}</div>}
        {loading ? <div className="loading">Carregando dados...</div> : <>
          {screen === 'Dashboard' && <Dashboard state={state} setScreen={setScreen} period={period} setPeriod={setPeriod} />}
          {screen === 'Ganhos' && <TransactionScreen title="Ganhos" type="INCOME" items={income} categories={state.categories} accounts={state.accounts} reload={load} />}
          {screen === 'Despesas Fixas' && <TransactionScreen title="Despesas Fixas" type="FIXED_EXPENSE" items={fixed} categories={state.categories} accounts={state.accounts} reload={load} />}
          {screen === 'Despesas Variáveis' && <TransactionScreen title="Despesas Variáveis" type="VARIABLE_EXPENSE" items={variable} categories={state.categories} accounts={state.accounts} reload={load} />}
          {screen === 'Dívidas' && <DebtScreen items={state.debts} accounts={state.accounts} reload={load} />}
          {screen === 'Economias' && <InvestmentScreen items={state.investments} accounts={state.accounts} reload={load} />}
          {screen === 'Metas' && <GoalScreen items={state.goals} reload={load} />}
          {screen === 'Categorias' && <CategoryScreen items={state.categories} reload={load} />}
          {screen === 'Relatórios' && <Reports state={state} period={period} setPeriod={setPeriod} />}
          {screen === 'Cartões/Faturas' && <CardInvoicesScreen transactions={state.transactions} accounts={state.accounts} period={period} setPeriod={setPeriod} />}
          {screen === 'Reembolsos' && <ReimbursementScreen items={state.reimbursements} accounts={state.accounts} reload={load} />}
          {screen === 'Contas Bancárias' && <BankAccountsScreen accounts={state.accounts} reload={load} />}
        </>}
      </main>
    </div>
  );
}

function Dashboard({ state, setScreen, period, setPeriod }) {
  const s = state.summary || {};
  const chartData = Object.keys(s.monthlyIncome || {}).map(k => ({ month: k, entradas: Number(s.monthlyIncome[k] || 0), saidas: Number((s.monthlyExpenses || {})[k] || 0) }));
  const pieData = (s.categoryUsage || []).map(c => ({ name: c.category, value: Number(c.spent || 0), color: c.color }));

  return <>
    <PeriodSelector period={period} setPeriod={setPeriod} />
    <div className="grid dashboard">
    <Metric title="Entradas do período" value={BRL.format(s.income || 0)} tone="green" />
    <Metric title="Saídas pagas" value={BRL.format(Number(s.fixedExpenses || 0) + Number(s.variableExpenses || 0))} tone="red" />
    <Metric title="Dívidas abertas" value={BRL.format(s.debtsOpen || 0)} tone="pink" />
    <Metric title="Economias" value={BRL.format(s.investments || 0)} tone="cyan" />
    <Metric title="Saldo nas contas" value={BRL.format(s.availableBalance || 0)} tone="lime" />

    <section className="card span2"><h2>Gastos por categoria</h2><div className="chartrow"><ResponsiveContainer width="50%" height={220}><PieChart><Pie data={pieData} dataKey="value" innerRadius={55} outerRadius={90}>{pieData.map((e, i) => <Cell key={i} fill={e.color || '#a3e635'} />)}</Pie><Tooltip formatter={v => BRL.format(v)} /></PieChart></ResponsiveContainer><div className="legend">{(s.categoryUsage || []).slice(0, 6).map(c => <p key={c.category}><span style={{ background: c.color }} /> {c.category}<b>{BRL.format(c.spent || 0)}</b></p>)}</div></div></section>
    <section className="card span2"><h2>Entradas vs Saídas</h2><ResponsiveContainer width="100%" height={240}><BarChart data={chartData}><Bar dataKey="entradas" radius={[8, 8, 0, 0]} /><Bar dataKey="saidas" radius={[8, 8, 0, 0]} /><Tooltip formatter={v => BRL.format(v)} /></BarChart></ResponsiveContainer></section>
    <section className="card"><h2>Contas bancárias</h2>{state.accounts.slice(0, 4).map(a => <p className="accountLine" key={a.id}><span>{a.name}</span><b>{BRL.format(a.balance || 0)}</b></p>)}<button className="ghost full" onClick={() => setScreen('Contas Bancárias')}>Gerenciar contas</button></section>
    <ListCard title="Ganhos recentes" items={state.transactions.filter(t => t.type === 'INCOME').slice(0, 4)} onMore={() => setScreen('Ganhos')} />
    <ListCard title="Despesas recentes" items={state.transactions.filter(t => t.type !== 'INCOME').slice(0, 4)} onMore={() => setScreen('Despesas Variáveis')} />
    <section className="card"><h2>Metas</h2>{state.goals.slice(0, 3).map(g => <Progress key={g.id} label={`${g.icon || '🎯'} ${g.name}`} percent={progress(g.currentAmount, g.targetAmount)} sub={`${BRL.format(g.currentAmount || 0)} / ${BRL.format(g.targetAmount || 0)}`} />)}</section>
  </div></>;
}

function PeriodSelector({ period, setPeriod }) {
  return <div className="periodBar card">
    <div><b>Período analisado</b><span>Dashboard e relatórios usam este mês como referência.</span></div>
    <select value={period.month} onChange={e => setPeriod({ ...period, month: Number(e.target.value) })}>
      {Array.from({ length: 12 }, (_, i) => i + 1).map(m => <option key={m} value={m}>{String(m).padStart(2, '0')}</option>)}
    </select>
    <input type="number" value={period.year} onChange={e => setPeriod({ ...period, year: Number(e.target.value || year) })} />
  </div>;
}

function Metric({ title, value, tone }) { return <section className={`metric ${tone}`}><span>{title}</span><strong>{value}</strong><small>Atualizado pelo saldo real das contas</small></section>; }

function ListCard({ title, items, onMore }) {
  return <section className="card span2"><h2>{title}</h2><table><tbody>{items.map(t => <tr key={t.id}><td>{t.description}</td><td>{t.category?.name}</td><td>{formatDate(t.date)}</td><td><b>{BRL.format(t.amount || 0)}</b></td><td>{t.type === 'VARIABLE_EXPENSE' ? t.paymentMethod : <Status status={getDisplayStatus(t)} />}</td></tr>)}</tbody></table><button className="ghost full" onClick={onMore}>Ver detalhes</button></section>;
}

function Status({ status }) {
  const txt = { RECEIVED: 'Recebido', PENDING: 'Pendente', PAID: 'Pago', OVERDUE: 'Em atraso' }[status] || status;
  return <span className={`status ${status}`}>{txt}</span>;
}

function Progress({ label, percent, sub }) {
  return <div className="progress"><div><b>{label}</b><span>{sub}</span></div><em>{percent}%</em><div className="bar"><i style={{ width: `${percent}%` }} /></div></div>;
}

function TransactionScreen({ title, type, items, categories, accounts, reload }) {
  const [open, setOpen] = useState(false);
  const filteredCategories = categories.filter(c => c.type === type);

  async function remove(id) {
    if (confirm('Excluir item? O saldo da conta será ajustado automaticamente.')) {
      await api.delete(`/transactions/${id}`);
      reload();
    }
  }

  async function updateStatus(transaction, status) {
    const finalStatus = transaction.type === 'FIXED_EXPENSE' && status === 'PENDING' && isPastDate(transaction.dueDate || transaction.date) ? 'OVERDUE' : status;
    await api.put(`/transactions/${transaction.id}`, buildTransactionPayload(transaction, finalStatus));
    reload();
  }

  async function updatePaymentMethod(transaction, paymentMethod) {
    await api.put(`/transactions/${transaction.id}`, { ...buildTransactionPayload(transaction, transaction.status), paymentMethod });
    reload();
  }

  return <section className="card page"><div className="sectionHead"><h2>{title}</h2><button onClick={() => setOpen(!open)}><Plus size={16} /> Novo lançamento</button></div>{open && <TransactionForm type={type} categories={filteredCategories} accounts={accounts} reload={reload} close={() => setOpen(false)} />}<DataTable type={type} items={items} remove={remove} updateStatus={updateStatus} updatePaymentMethod={updatePaymentMethod} /></section>;
}

function TransactionForm({ type, categories, accounts, reload, close }) {
  const [form, setForm] = useState({ description: '', amount: '', date: new Date().toISOString().slice(0, 10), status: type === 'INCOME' ? 'RECEIVED' : type === 'FIXED_EXPENSE' ? 'PENDING' : 'PAID', categoryId: '', accountId: '', paymentMethod: 'Pix', reimbursable: false, reimbursementCompany: '', reimbursementEmail: '' });
  const [receiptFile, setReceiptFile] = useState(null);
  const dateLabel = type === 'FIXED_EXPENSE' ? 'Data de vencimento' : 'Data';

  async function submit(e) {
    e.preventDefault();
    if (!form.accountId) return alert('Selecione a conta bancária deste lançamento.');
    const finalStatus = type === 'INCOME' ? 'RECEIVED' : type === 'FIXED_EXPENSE' && form.status === 'PENDING' && isPastDate(form.date) ? 'OVERDUE' : type === 'VARIABLE_EXPENSE' ? 'PAID' : form.status;
    const created = await api.post('/transactions', { ...form, type, status: finalStatus, dueDate: type === 'FIXED_EXPENSE' ? form.date : null, amount: Number(form.amount), reimbursable: type !== 'INCOME' && !!form.reimbursable, reimbursementStatus: form.reimbursable ? 'TO_SEND' : 'NOT_REIMBURSABLE', categoryId: Number(form.categoryId) || null, accountId: Number(form.accountId) || null });
    if (receiptFile && created?.id) {
      const data = new FormData();
      data.append('file', receiptFile);
      await api.upload(`/transactions/${created.id}/receipt`, data);
    }
    close();
    reload();
  }

  return <form className="form" onSubmit={submit}>
    <input placeholder="Descrição" value={form.description} onChange={e => setForm({ ...form, description: e.target.value })} required />
    <input type="number" step="0.01" placeholder="Valor" value={form.amount} onChange={e => setForm({ ...form, amount: e.target.value })} required />
    <label className="field"><span>{dateLabel}</span><input type="date" value={form.date} onChange={e => setForm({ ...form, date: e.target.value })} required /></label>
    <select value={form.categoryId} onChange={e => setForm({ ...form, categoryId: e.target.value })} required><option value="">Categoria</option>{categories.map(c => <option value={c.id} key={c.id}>{c.icon} {c.name}</option>)}</select>
    <select value={form.accountId} onChange={e => setForm({ ...form, accountId: e.target.value })} required><option value="">Conta bancária</option>{accounts.map(a => <option value={a.id} key={a.id}>{a.name} — {BRL.format(a.balance || 0)}</option>)}</select>
    {type === 'FIXED_EXPENSE' && <select value={form.status} onChange={e => setForm({ ...form, status: e.target.value })}><option value="PAID">Pago</option><option value="PENDING">Pendente</option><option value="OVERDUE">Em atraso</option></select>}
    {type !== 'INCOME' && <select value={form.paymentMethod} onChange={e => setForm({ ...form, paymentMethod: e.target.value })}>{paymentMethods.map(m => <option key={m} value={m}>{m}</option>)}</select>}
    {type !== 'INCOME' && <label className="checkField"><input type="checkbox" checked={form.reimbursable} onChange={e => setForm({ ...form, reimbursable: e.target.checked })} /> Gasto reembolsável</label>}
    {type !== 'INCOME' && form.reimbursable && <input placeholder="Empresa responsável pelo reembolso" value={form.reimbursementCompany} onChange={e => setForm({ ...form, reimbursementCompany: e.target.value })} />}
    {type !== 'INCOME' && form.reimbursable && <input type="email" placeholder="E-mail para envio do reembolso" value={form.reimbursementEmail} onChange={e => setForm({ ...form, reimbursementEmail: e.target.value })} />}
    {type !== 'INCOME' && <label className="field fullLine"><span>Comprovante opcional, PDF, PNG, JPG ou JPEG</span><input type="file" accept="application/pdf,image/png,image/jpeg" onChange={e => setReceiptFile(e.target.files?.[0] || null)} /></label>}
    <button>Salvar</button>
  </form>;
}

function DataTable({ type, items, remove, updateStatus, updatePaymentMethod }) {
  const dateHeader = type === 'FIXED_EXPENSE' ? 'Data de vencimento' : 'Data';
  const actionHeader = type === 'VARIABLE_EXPENSE' ? 'Forma de pagamento' : type === 'FIXED_EXPENSE' ? 'Status / pagamento' : 'Status';
  return <table><thead><tr><th>Descrição</th><th>Categoria</th><th>Conta</th><th>{dateHeader}</th><th>Valor</th><th>Comprovante</th><th>{actionHeader}</th><th></th></tr></thead><tbody>{items.map(t => {
    const status = getDisplayStatus(t);
    return <tr key={t.id}><td>{t.description} {t.reimbursable && <span className="miniBadge">reembolso</span>}</td><td>{t.category?.icon} {t.category?.name}</td><td>{t.account?.name || '-'}</td><td>{formatDate(t.dueDate || t.date)}</td><td><b>{BRL.format(t.amount || 0)}</b></td><td>{t.receiptFileName ? <a className="receiptLink" href={receiptUrl(t.receiptFileName)} target="_blank" rel="noreferrer">Ver comprovante</a> : '-'}</td><td>{t.type === 'INCOME' && <Status status="RECEIVED" />}{t.type === 'FIXED_EXPENSE' && <div className="stackedControls"><select className="statusSelect" value={status} onChange={e => updateStatus(t, e.target.value)}><option value="PAID">Pago</option><option value="PENDING">Pendente</option><option value="OVERDUE">Em atraso</option></select><select className="statusSelect" value={t.paymentMethod || 'Débito'} onChange={e => updatePaymentMethod(t, e.target.value)}>{paymentMethods.map(m => <option key={m} value={m}>{m}</option>)}</select></div>}{t.type === 'VARIABLE_EXPENSE' && <select className="statusSelect" value={t.paymentMethod || 'Pix'} onChange={e => updatePaymentMethod(t, e.target.value)}>{paymentMethods.map(m => <option key={m} value={m}>{m}</option>)}</select>}</td><td><button className="icon" onClick={() => remove(t.id)}><Trash2 size={15} /></button></td></tr>;
  })}</tbody></table>;
}

function DebtScreen({ items, accounts, reload }) {
  return <CrudScreen title="Dívidas" path="debts" fields={[[ 'name', 'Descrição da dívida' ], [ 'creditor', 'Credor' ], [ 'totalAmount', 'Valor total da dívida', 'number' ], [ 'paidAmount', 'Valor já pago', 'number' ], [ 'totalInstallments', 'Número de parcelas totais', 'number' ], [ 'monthlyPayment', 'Valor de cada parcela', 'number' ], [ 'nextDueDate', 'Próximo vencimento', 'date' ]]} reload={reload} items={items} headers={[ 'Dívida', 'Credor', 'Total', 'Pago', 'Restante', 'Parcelas', 'Parcela', 'Status', 'Progresso', 'Pagar parcela', '' ]} render={d => <DebtRow key={d.id} debt={d} accounts={accounts} reload={reload} />} />;
}

function DebtRow({ debt, accounts, reload }) {
  const remaining = remainingDebt(debt);
  const suggestedPayment = Math.min(Number(debt.monthlyPayment || 0), remaining);
  const [accountId, setAccountId] = useState(accounts[0]?.id || '');
  const [amount, setAmount] = useState(suggestedPayment || 0);
  const percent = progress(debt.paidAmount, debt.totalAmount);
  const completed = percent >= 100 || remaining <= 0;

  async function payDebt() {
    if (completed) return;
    if (!accountId) return alert('Selecione a conta usada para pagar a parcela.');
    const payment = Number(amount || 0);
    if (payment <= 0) return alert('Informe um valor maior que zero.');
    if (payment > remaining) return alert(`O valor máximo para quitar essa dívida é ${BRL.format(remaining)}.`);
    await api.post(`/debts/${debt.id}/pay`, { accountId: Number(accountId), amount: payment });
    reload();
  }

  return <tr><td>{debt.name}</td><td>{debt.creditor}</td><td><b>{BRL.format(debt.totalAmount || 0)}</b></td><td>{BRL.format(debt.paidAmount || 0)}</td><td>{BRL.format(remaining)}</td><td>{debt.totalInstallments || 1}x</td><td>{BRL.format(debt.monthlyPayment || 0)}</td><td><Status status={getDebtStatus(debt)} /></td><td><Progress label={completed ? '🎉 Dívida quitada!' : ''} percent={percent} sub={`${BRL.format(debt.paidAmount || 0)} de ${BRL.format(debt.totalAmount || 0)}`} /></td><td>{completed ? <b className="good">Quitada</b> : <div className="payDebt"><select value={accountId} onChange={e => setAccountId(e.target.value)}>{accounts.map(a => <option value={a.id} key={a.id}>{a.name}</option>)}</select><input type="number" step="0.01" min="0.01" max={remaining} value={amount} onChange={e => setAmount(e.target.value)} /><button onClick={payDebt}>{Number(amount || 0) >= remaining ? 'Pagar restante' : 'Pagar'}</button></div>}</td><td><Delete path="debts" id={debt.id} reload={reload} /></td></tr>;
}

function GoalScreen({ items, reload }) {
  return <CrudScreen title="Metas" path="goals" fields={[[ 'name', 'Nome da meta' ], [ 'icon', 'Ícone', 'iconSelect' ], [ 'targetAmount', 'Valor da meta', 'number' ], [ 'currentAmount', 'Valor atual', 'number' ], [ 'deadline', 'Data limite', 'date' ]]} reload={reload} items={items} headers={[ 'Meta', 'Valor da meta', 'Progresso', 'Data limite', 'Atualizar valor atual', '' ]} render={g => <GoalRow key={g.id} goal={g} reload={reload} />} />;
}

function GoalRow({ goal, reload }) {
  const [currentAmount, setCurrentAmount] = useState(goal.currentAmount || 0);
  const percent = progress(goal.currentAmount, goal.targetAmount);
  const completed = percent >= 100;

  async function updateCurrentAmount() {
    const nextValue = Number(currentAmount || 0);
    if (nextValue < 0) return alert('O valor atual não pode ser negativo.');
    if (nextValue > Number(goal.targetAmount || 0)) return alert(`O valor atual não pode passar da meta de ${BRL.format(goal.targetAmount || 0)}.`);
    await api.put(`/goals/${goal.id}`, { ...goal, currentAmount: nextValue });
    reload();
  }

  return <tr><td>{goal.icon || '🎯'} {goal.name}</td><td>{BRL.format(goal.targetAmount || 0)}</td><td><Progress label={completed ? '🎉 Meta concluída!' : ''} percent={percent} sub={`${BRL.format(goal.currentAmount || 0)} de ${BRL.format(goal.targetAmount || 0)}`} /></td><td>{formatDate(goal.deadline)}</td><td><div className="inlineEdit"><input type="number" step="0.01" min="0" max={goal.targetAmount || 0} value={currentAmount} onChange={e => setCurrentAmount(e.target.value)} /><button onClick={updateCurrentAmount}>Salvar</button></div></td><td><Delete path="goals" id={goal.id} reload={reload} /></td></tr>;
}

function InvestmentScreen({ items, accounts, reload }) {
  return <CrudScreen title="Economias / Investimentos" path="investments" fields={[[ 'name', 'Descrição da economia/investimento' ], [ 'investmentType', 'Onde está guardado/investido' ], [ 'accountId', 'Conta de origem', 'accountSelect' ], [ 'amount', 'Valor guardado', 'number' ], [ 'profitabilityPercent', 'Rentabilidade mensal %', 'number' ]]} accounts={accounts} reload={reload} items={items} headers={[ 'Descrição', 'Local / Instituição', 'Conta de origem', 'Valor guardado', 'Rentabilidade mensal', 'Atualizar valor guardado', '' ]} render={i => <InvestmentRow key={i.id} investment={i} reload={reload} />} />;
}

function InvestmentRow({ investment, reload }) {
  const [amount, setAmount] = useState(investment.amount || 0);

  async function updateAmount() {
    const nextValue = Number(amount || 0);
    if (nextValue < 0) return alert('O valor guardado não pode ser negativo.');
    await api.put(`/investments/${investment.id}`, {
      name: investment.name,
      investmentType: investment.investmentType,
      amount: nextValue,
      profitabilityPercent: Number(investment.profitabilityPercent || 0),
      accountId: investment.account?.id || null
    });
    reload();
  }

  return <tr><td>{investment.name}</td><td>{investment.investmentType}</td><td>{investment.account?.name || '-'}</td><td>{BRL.format(investment.amount || 0)}</td><td className="good">+{investment.profitabilityPercent || 0}%</td><td><div className="inlineEdit"><input type="number" step="0.01" min="0" value={amount} onChange={e => setAmount(e.target.value)} /><button onClick={updateAmount}>Salvar</button></div></td><td><Delete path="investments" id={investment.id} reload={reload} /></td></tr>;
}

function CategoryScreen({ items, reload }) {
  return <CrudScreen title="Categorias e Limites" path="categories" fields={[[ 'name', 'Nome' ], [ 'icon', 'Ícone', 'categoryIconSelect' ], [ 'color', 'Cor', 'color' ], [ 'monthlyLimit', 'Limite mensal', 'number' ], [ 'type', 'Tipo', 'select' ]]} reload={reload} items={items} headers={[ 'Categoria', 'Tipo', 'Limite mensal', '' ]} render={c => <tr key={c.id}><td><span className="dot" style={{ background: c.color }} /> {c.icon} {c.name}</td><td>{categoryTypeLabel(c.type)}</td><td>{BRL.format(c.monthlyLimit || 0)}</td><td><Delete path="categories" id={c.id} reload={reload} /></td></tr>} />;
}

function BankAccountsScreen({ accounts, reload }) {
  return <CrudScreen title="Contas Bancárias" path="accounts" fields={[[ 'name', 'Nome da conta' ], [ 'cardLimit', 'Limite disponível do cartão', 'number' ], [ 'balance', 'Saldo disponível', 'number' ]]} reload={reload} items={accounts} headers={[ 'Conta', 'Limite disponível do cartão', 'Saldo disponível', '' ]} render={a => <tr key={a.id}><td>{a.name}</td><td>{BRL.format(a.cardLimit || 0)}</td><td><b>{BRL.format(a.balance || 0)}</b></td><td><Delete path="accounts" id={a.id} reload={reload} /></td></tr>} />;
}

function CrudScreen({ title, path, fields, items, render, reload, headers, accounts = [] }) {
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({});
  async function submit(e) {
    e.preventDefault();
    const body = { ...form };
    ['amount', 'totalAmount', 'paidAmount', 'monthlyPayment', 'totalInstallments', 'targetAmount', 'currentAmount', 'monthlyLimit', 'balance', 'profitabilityPercent', 'cardLimit', 'accountId'].forEach(k => { if (body[k] !== undefined && body[k] !== '') body[k] = Number(body[k] || 0); });
    if (path === 'categories' && !body.type) body.type = 'VARIABLE_EXPENSE';
    if (path === 'goals' && !body.icon) body.icon = '🎯';
    if (path === 'categories' && !body.icon) body.icon = '💰';
    if (path === 'categories' && !body.color) body.color = '#a3e635';
    if (path === 'accounts' && body.cardLimit === undefined) body.cardLimit = 0;
    if (path === 'investments' && !body.accountId) return alert('Selecione a conta de origem da economia/investimento.');
    await api.post(`/${path}`, body);
    setForm({}); setOpen(false); reload();
  }
  return <section className="card page"><div className="sectionHead"><h2>{title}</h2><button onClick={() => setOpen(!open)}><Plus size={16} /> Novo</button></div>{open && <form className="form" onSubmit={submit}>{fields.map(([name, label, type]) => {
    if (type === 'select') return <select key={name} value={form[name] || ''} onChange={e => setForm({ ...form, [name]: e.target.value })}><option value="">Tipo</option><option value="INCOME">Ganho</option><option value="FIXED_EXPENSE">Despesa fixa</option><option value="VARIABLE_EXPENSE">Despesa variável</option></select>;
    if (type === 'iconSelect') return <select key={name} value={form[name] || '🎯'} onChange={e => setForm({ ...form, [name]: e.target.value })}><option value="">Selecione um ícone</option>{goalIcons.map(icon => <option key={icon} value={icon}>{icon}</option>)}</select>;
    if (type === 'categoryIconSelect') return <select key={name} value={form[name] || '💰'} onChange={e => setForm({ ...form, [name]: e.target.value })}><option value="">Selecione um ícone</option>{categoryIcons.map(icon => <option key={icon} value={icon}>{icon}</option>)}</select>;
    if (type === 'accountSelect') return <select key={name} value={form[name] || ''} onChange={e => setForm({ ...form, [name]: e.target.value })} required><option value="">Conta de origem</option>{accounts.map(a => <option value={a.id} key={a.id}>{a.name} — {BRL.format(a.balance || 0)}</option>)}</select>;
    if (type === 'color') return <label className="field" key={name}><span>{label}</span><input type="color" value={form[name] || '#a3e635'} onChange={e => setForm({ ...form, [name]: e.target.value })} /></label>;
    return <input key={name} type={type || 'text'} step="0.01" placeholder={label} value={form[name] || ''} onChange={e => setForm({ ...form, [name]: e.target.value })} required={name === 'name'} />;
  })}<button>Salvar</button></form>}<table>{headers && <thead><tr>{headers.map((header, i) => <th key={`${header}-${i}`}>{header}</th>)}</tr></thead>}<tbody>{items.map(render)}</tbody></table></section>;
}

function Delete({ path, id, reload }) {
  return <button className="icon" onClick={async () => { if (confirm('Excluir?')) { await api.delete(`/${path}/${id}`); reload(); } }}><Trash2 size={15} /></button>;
}

function CardInvoicesScreen({ transactions, accounts, period, setPeriod }) {
  const creditExpenses = transactions.filter(t => t.type !== 'INCOME' && (t.paymentMethod || '').toLowerCase().normalize('NFD').replace(/[\u0300-\u036f]/g, '') === 'credito');
  const current = creditExpenses.filter(t => isInPeriod(t.date, period));
  const usedByAccount = accounts.map(account => {
    const expenses = current.filter(t => t.account?.id === account.id);
    const used = expenses.reduce((sum, t) => sum + Number(t.amount || 0), 0);
    const available = Number(account.cardLimit || 0);
    return { account, expenses, used, available, projectedLimit: available };
  });
  const totalUsed = usedByAccount.reduce((sum, item) => sum + item.used, 0);

  return <section className="card page">
    <div className="sectionHead"><div><h2>Cartões / Faturas</h2><p className="muted">Controle dos gastos no crédito por conta/cartão. Compras no crédito reduzem o limite disponível, mas não reduzem o saldo bancário imediatamente.</p></div><strong>{BRL.format(totalUsed)} na fatura do período</strong></div>
    <PeriodSelector period={period} setPeriod={setPeriod} />
    <div className="invoiceGrid">
      {usedByAccount.map(({ account, expenses, used, available }) => <section className="invoiceCard" key={account.id}>
        <div className="invoiceHead"><div><h3>{account.name}</h3><span>{expenses.length} compra(s) no crédito</span></div><b>{BRL.format(used)}</b></div>
        <Progress label="Limite usado no período" percent={available + used > 0 ? Math.round((used / (available + used)) * 100) : 0} sub={`Limite disponível atual: ${BRL.format(available)}`} />
        <table><tbody>{expenses.length === 0 ? <tr><td className="muted">Nenhum gasto no crédito neste período.</td></tr> : expenses.map(t => <tr key={t.id}><td>{t.description}<br /><small>{t.category?.name || '-'}</small></td><td>{formatDate(t.date)}</td><td><b>{BRL.format(t.amount || 0)}</b></td></tr>)}</tbody></table>
      </section>)}
    </div>
  </section>;
}

function Reports({ state, period, setPeriod }) {
  const s = state.summary || {};
  const data = Object.keys(s.monthlyIncome || {}).map(k => ({ month: k, saldo: Number(s.monthlyIncome[k]) - Number((s.monthlyExpenses || {})[k] || 0), entradas: Number(s.monthlyIncome[k] || 0), saidas: Number((s.monthlyExpenses || {})[k] || 0) }));
  const monthTransactions = state.transactions.filter(t => isInPeriod(t.date, period));
  const income = monthTransactions.filter(t => t.type === 'INCOME').reduce((sum, t) => sum + Number(t.amount || 0), 0);
  const expenses = monthTransactions.filter(t => t.type !== 'INCOME' && (t.type === 'VARIABLE_EXPENSE' || t.status === 'PAID')).reduce((sum, t) => sum + Number(t.amount || 0), 0);
  const reimbursableOpen = state.reimbursements.filter(r => r.reimbursementStatus !== 'REIMBURSED' && r.reimbursementStatus !== 'REJECTED').reduce((sum, r) => sum + Number(r.amount || 0), 0);
  const categories = (s.categoryUsage || []).filter(c => Number(c.spent || 0) > 0);

  function exportCsv() {
    const rows = [['Data', 'Descrição', 'Tipo', 'Categoria', 'Conta', 'Forma/Status', 'Valor']];
    monthTransactions.forEach(t => rows.push([formatDate(t.date), t.description, categoryTypeLabel(t.type), t.category?.name || '', t.account?.name || '', t.type === 'VARIABLE_EXPENSE' ? t.paymentMethod : getDisplayStatus(t), Number(t.amount || 0).toFixed(2).replace('.', ',')]));
    const csv = rows.map(row => row.map(value => `"${String(value).replaceAll('"', '""')}"`).join(';')).join('\n');
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `finanzero-relatorio-${period.year}-${String(period.month).padStart(2, '0')}.csv`;
    link.click();
    URL.revokeObjectURL(url);
  }

  return <section className="card page">
    <div className="sectionHead"><div><h2>Relatórios</h2><p className="muted">Resumo do período, evolução anual, categorias e exportação dos lançamentos.</p></div><button onClick={exportCsv}>Exportar CSV</button></div>
    <PeriodSelector period={period} setPeriod={setPeriod} />
    <div className="reportCards">
      <Metric title="Entradas do período" value={BRL.format(income)} tone="green" />
      <Metric title="Saídas do período" value={BRL.format(expenses)} tone="red" />
      <Metric title="Resultado do período" value={BRL.format(income - expenses)} tone={income - expenses >= 0 ? 'lime' : 'pink'} />
      <Metric title="Reembolsos em aberto" value={BRL.format(reimbursableOpen)} tone="cyan" />
    </div>
    <section className="card innerCard"><h2>Evolução anual</h2><ResponsiveContainer width="100%" height={320}><LineChart data={data}><Line dataKey="saldo" strokeWidth={3} /><Line dataKey="entradas" strokeWidth={2} /><Line dataKey="saidas" strokeWidth={2} /><Tooltip formatter={v => BRL.format(v)} /></LineChart></ResponsiveContainer></section>
    <section className="card innerCard"><h2>Gastos por categoria no período</h2><table><thead><tr><th>Categoria</th><th>Gasto</th><th>Limite</th><th>Uso</th></tr></thead><tbody>{categories.length === 0 ? <tr><td colSpan="4" className="muted">Nenhum gasto no período selecionado.</td></tr> : categories.map(c => <tr key={c.category}><td><span className="dot" style={{ background: c.color }} /> {c.category}</td><td><b>{BRL.format(c.spent || 0)}</b></td><td>{BRL.format(c.monthlyLimit || 0)}</td><td>{Math.round(c.percent || 0)}%</td></tr>)}</tbody></table></section>
  </section>;
}


function Root() {
  const [theme, setTheme] = useState(() => localStorage.getItem('finanzero_theme') || 'dark');

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem('finanzero_theme', theme);
  }, [theme]);

  return <App theme={theme} setTheme={setTheme} />;
}

createRoot(document.getElementById('root')).render(<Root />);

