import React, { useEffect, useMemo, useState } from 'react';
import { createRoot } from 'react-dom/client';
import { Download, Eye, LogOut, Plus, Receipt, Send, Trash2 } from 'lucide-react';
import './style.css';

const API = 'http://localhost:3001/api';
const money = v => Number(v || 0).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' });
const today = () => new Date().toISOString().slice(0, 10);
const currentMonth = () => new Date().toISOString().slice(0, 7);

function App() {
  const [token, setToken] = useState(localStorage.getItem('token'));
  const [user, setUser] = useState(JSON.parse(localStorage.getItem('user') || 'null'));
  const [mode, setMode] = useState('login');
  const [authForm, setAuthForm] = useState({ name: '', email: '', password: '' });
  const [expenses, setExpenses] = useState([]);
  const [dashboard, setDashboard] = useState({ total: 0, count: 0, byStatus: {}, last: null });
  const [options, setOptions] = useState({ categories: ['Outros'], statuses: [] });
  const [preview, setPreview] = useState(null);
  const [msg, setMsg] = useState('');
  const [month, setMonth] = useState(currentMonth());
  const [filters, setFilters] = useState({ status: '', category: '' });
  const [exportEmail, setExportEmail] = useState('');
  const [form, setForm] = useState({ amount: '', description: '', expenseDate: today(), category: 'Outros', receipt: null });

  const isAdmin = user?.role === 'admin';
  const headers = useMemo(() => ({ Authorization: `Bearer ${token}` }), [token]);

  async function request(path, options = {}) {
    const res = await fetch(`${API}${path}`, options);
    const data = res.status === 204 ? null : await res.json().catch(() => null);
    if (!res.ok) throw new Error(data?.message || 'Erro na requisição.');
    return data;
  }

  function qs() {
    const p = new URLSearchParams({ month });
    if (filters.status) p.set('status', filters.status);
    if (filters.category) p.set('category', filters.category);
    return p.toString();
  }

  async function handleAuth(e) {
    e.preventDefault();
    setMsg('');
    try {
      const payload = mode === 'register' ? authForm : { email: authForm.email, password: authForm.password };
      const data = await request(`/auth/${mode}`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(payload)
      });
      localStorage.setItem('token', data.token);
      localStorage.setItem('user', JSON.stringify(data.user));
      setToken(data.token); setUser(data.user);
    } catch (err) { setMsg(err.message); }
  }

  async function loadAll() {
    if (!token) return;
    const query = qs();
    const [list, dash, opts] = await Promise.all([
      request(`/expenses?${query}`, { headers }),
      request(`/expenses/dashboard?${query}`, { headers }),
      request('/expenses/options', { headers })
    ]);
    setExpenses(list);
    setDashboard(dash);
    setOptions(opts);
    if (!opts.categories.includes(form.category)) setForm(f => ({ ...f, category: opts.categories[0] || 'Outros' }));
  }

  useEffect(() => { loadAll().catch(err => setMsg(err.message)); }, [token, month, filters.status, filters.category]);

  async function addExpense(e) {
    e.preventDefault(); setMsg('');
    try {
      const fd = new FormData();
      fd.append('amount', form.amount);
      fd.append('description', form.description);
      fd.append('expenseDate', form.expenseDate);
      fd.append('category', form.category);
      if (form.receipt) fd.append('receipt', form.receipt);
      await request('/expenses', { method: 'POST', headers, body: fd });
      setForm({ amount: '', description: '', expenseDate: today(), category: form.category, receipt: null });
      await loadAll();
      setMsg('Lançamento salvo.');
    } catch (err) { setMsg(err.message); }
  }

  async function removeExpense(id) {
    if (!confirm('Excluir este lançamento?')) return;
    await request(`/expenses/${id}`, { method: 'DELETE', headers });
    await loadAll();
  }

  async function updateStatus(id, status) {
    await request(`/expenses/${id}/status`, {
      method: 'PATCH', headers: { ...headers, 'Content-Type': 'application/json' }, body: JSON.stringify({ status })
    });
    await loadAll();
  }

  async function loadPreview() {
    setMsg('');
    try {
      const data = await request(`/reports/preview?month=${month}`, { headers });
      setPreview(data);
    } catch (err) { setMsg(err.message); }
  }

  async function sendReport() {
    setMsg('');
    try {
      const data = await request('/reports/send', {
        method: 'POST', headers: { ...headers, 'Content-Type': 'application/json' }, body: JSON.stringify({ month })
      });
      setMsg(data.message);
      await loadPreview();
    } catch (err) { setMsg(err.message); }
  }

  async function exportCsv() {
    const res = await fetch(`${API}/reports/export.csv?month=${month}`, { headers });
    if (!res.ok) {
      const data = await res.json().catch(() => null);
      setMsg(data?.message || 'Erro ao exportar relatório.');
      return;
    }
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `reembolsos-${month}.csv`;
    a.click();
    URL.revokeObjectURL(url);
  }

  async function exportByEmail() {
    setMsg('');
    try {
      const data = await request('/reports/export-email', {
        method: 'POST',
        headers: { ...headers, 'Content-Type': 'application/json' },
        body: JSON.stringify({
          email: exportEmail,
          month,
          status: filters.status,
          category: filters.category
        })
      });
      setMsg(data.message);
    } catch (err) { setMsg(err.message); }
  }

  async function openReceipt(filename, originalName = 'comprovante') {
    const res = await fetch(`${API}/expenses/receipt/${filename}`, { headers });
    if (!res.ok) { setMsg('Não foi possível abrir o comprovante.'); return; }
    const blob = await res.blob();
    const url = URL.createObjectURL(blob);
    window.open(url, '_blank');
    setTimeout(() => URL.revokeObjectURL(url), 60000);
  }

  function logout() {
    localStorage.clear(); setToken(null); setUser(null); setExpenses([]); setPreview(null);
  }

  if (!token) return <main className="auth-page">
    <section className="auth-card">
      <div className="brand"><div className="logo"><Receipt size={24}/></div><div><h1>Reembolso Clean</h1><p>Controle rápido de despesas e comprovantes.</p></div></div>
      <form onSubmit={handleAuth} className="form">
        {mode === 'register' && <input placeholder="Nome" value={authForm.name} onChange={e=>setAuthForm({...authForm,name:e.target.value})}/>} 
        <input placeholder="E-mail" type="email" value={authForm.email} onChange={e=>setAuthForm({...authForm,email:e.target.value})}/>
        <input placeholder="Senha" type="password" value={authForm.password} onChange={e=>setAuthForm({...authForm,password:e.target.value})}/>
        <button>{mode === 'login' ? 'Entrar' : 'Criar conta'}</button>
      </form>
      <button className="link" onClick={()=>setMode(mode === 'login' ? 'register' : 'login')}>{mode === 'login' ? 'Criar cadastro' : 'Já tenho conta'}</button>
      {msg && <p className="msg">{msg}</p>}
    </section>
  </main>;

  return <main className="app">
    <header>
      <div><h1>Despesas</h1><p>Olá, {user?.name}. {isAdmin ? 'Você está no perfil administrador.' : 'Você está no perfil usuário.'}</p></div>
      <button className="ghost" onClick={logout}><LogOut size={16}/> Sair</button>
    </header>

    <section className="cards">
      <div className="card"><span>Total filtrado</span><strong>{money(dashboard.total)}</strong></div>
      <div className="card"><span>Lançamentos</span><strong>{dashboard.count}</strong></div>
      <div className="card"><span>Último lançamento</span><strong>{dashboard.last ? money(dashboard.last.amount) : '-'}</strong><small>{dashboard.last?.description || 'Nenhum'}</small></div>
      <div className="card"><span>Pendentes</span><strong>{dashboard.byStatus?.Pendente || 0}</strong></div>
    </section>

    <section className="grid">
      <form className="panel form" onSubmit={addExpense}>
        <h2><Plus size={18}/> Novo lançamento</h2>
        <label>Valor obrigatório<input required placeholder="Ex: 125,90" value={form.amount} onChange={e=>setForm({...form, amount:e.target.value})}/></label>
        <label>Descrição obrigatória<input required placeholder="Ex: Material de escritório" value={form.description} onChange={e=>setForm({...form, description:e.target.value})}/></label>
        <label>Data obrigatória<input required type="date" value={form.expenseDate} onChange={e=>setForm({...form, expenseDate:e.target.value})}/></label>
        <label>Categoria<select value={form.category} onChange={e=>setForm({...form, category:e.target.value})}>{options.categories.map(c => <option key={c}>{c}</option>)}</select></label>
        <label>Comprovante opcional<input type="file" accept="image/*,.pdf" onChange={e=>setForm({...form, receipt:e.target.files?.[0] || null})}/><small>PDF, JPG, PNG ou WEBP até 8MB.</small></label>
        <button>Salvar lançamento</button>
        {msg && <p className="msg">{msg}</p>}
      </form>

      <section className="panel summary">
        <h2>Filtros e relatório</h2>
        <label>Mês<input type="month" value={month} onChange={e=>setMonth(e.target.value)}/></label>
        <label>Status<select value={filters.status} onChange={e=>setFilters({...filters, status:e.target.value})}><option value="">Todos</option>{options.statuses.map(s => <option key={s}>{s}</option>)}</select></label>
        <label>Categoria<select value={filters.category} onChange={e=>setFilters({...filters, category:e.target.value})}><option value="">Todas</option>{options.categories.map(c => <option key={c}>{c}</option>)}</select></label>
        <div className="export-box">
          <label>E-mail para exportar relatório<input type="email" placeholder="destinatario@empresa.com" value={exportEmail} onChange={e=>setExportEmail(e.target.value)}/></label>
          <button className="secondary send" onClick={exportByEmail} disabled={!exportEmail}><Send size={16}/> Exportar e enviar por e-mail</button>
          <p className="hint">O relatório usa o mês, status e categoria selecionados acima. Usuário comum exporta apenas os próprios lançamentos.</p>
        </div>
        {isAdmin ? <>
          <button className="secondary" onClick={loadPreview}><Eye size={16}/> Ver prévia do e-mail automático</button>
          <button className="secondary" onClick={exportCsv}><Download size={16}/> Baixar CSV</button>
          <button className="secondary send" onClick={sendReport}><Send size={16}/> Enviar resumo mensal padrão</button>
          <p className="hint">O envio automático roda todo dia 1º, às 08h, com o resumo do mês anterior.</p>
        </> : <p className="hint">Alteração de status e relatório mensal padrão ficam com o administrador.</p>}
      </section>
    </section>

    {preview && isAdmin && <section className="panel preview">
      <div className="preview-head"><h2>Prévia do e-mail: {preview.month}</h2><button className="ghost" onClick={()=>setPreview(null)}>Fechar</button></div>
      <p>Destinatário: <b>{preview.reportTo || 'não configurado'}</b></p>
      <p>Total: <b>{preview.total}</b> em <b>{preview.count}</b> lançamento(s).</p>
      {preview.lastSentAt && <p>Último envio deste mês: {new Date(preview.lastSentAt.replace(' ', 'T')).toLocaleString('pt-BR')}</p>}
      <div className="mini-grid">
        <div><h3>Por usuário</h3>{Object.entries(preview.byUser).map(([k,v]) => <p key={k}>{k}: <b>{money(v)}</b></p>)}</div>
        <div><h3>Por categoria</h3>{Object.entries(preview.byCategory).map(([k,v]) => <p key={k}>{k}: <b>{money(v)}</b></p>)}</div>
      </div>
    </section>}

    <section className="panel table-panel">
      <h2>Lançamentos</h2>
      <div className="table">
        {expenses.map(e => <div className="row" key={e.id}>
          <div><b>{e.description}</b><small>{new Date(e.expense_date + 'T00:00:00').toLocaleDateString('pt-BR')} • {e.category} {isAdmin ? `• ${e.user_name}` : ''}</small></div>
          <div><span className={`badge b-${String(e.status).toLowerCase().replaceAll(' ', '-')}`}>{e.status}</span></div>
          <div className="right"><b>{money(e.amount)}</b>{e.receipt_filename && <button className="receipt-link" onClick={()=>openReceipt(e.receipt_filename, e.receipt_original_name)}>Comprovante</button>}</div>
          {isAdmin && <select className="status" value={e.status} onChange={ev=>updateStatus(e.id, ev.target.value)}>{options.statuses.map(s => <option key={s}>{s}</option>)}</select>}
          <button className="icon" onClick={()=>removeExpense(e.id)}><Trash2 size={16}/></button>
        </div>)}
        {!expenses.length && <p className="empty">Nenhum lançamento nesse filtro.</p>}
      </div>
    </section>
  </main>;
}

createRoot(document.getElementById('root')).render(<App />);
