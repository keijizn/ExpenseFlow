import fs from 'fs';
import path from 'path';

const dataDir = path.resolve('data');
const dataPath = path.join(dataDir, 'database.json');

const initial = { users: [], expenses: [], reportLogs: [], counters: { users: 1, expenses: 1, reportLogs: 1 } };

function ensure() {
  if (!fs.existsSync(dataDir)) fs.mkdirSync(dataDir, { recursive: true });
  if (!fs.existsSync(dataPath)) fs.writeFileSync(dataPath, JSON.stringify(initial, null, 2));
}

function read() {
  ensure();
  return JSON.parse(fs.readFileSync(dataPath, 'utf8'));
}

function write(data) {
  ensure();
  fs.writeFileSync(dataPath, JSON.stringify(data, null, 2));
}

export async function initStore() { ensure(); }

export async function createUser({ name, email, password_hash, role }) {
  const data = read();
  const user = { id: data.counters.users++, name, email, password_hash, role, created_at: new Date().toISOString() };
  data.users.push(user); write(data); return user;
}

export async function findUserByEmail(email) { return read().users.find(u => u.email === email) || null; }
export async function findUserById(id) { return read().users.find(u => Number(u.id) === Number(id)) || null; }

export async function createExpense(expense) {
  const data = read();
  const saved = { id: data.counters.expenses++, status: 'Pendente', created_at: new Date().toISOString(), updated_at: null, ...expense };
  data.expenses.push(saved); write(data); return saved;
}

export async function listExpenses(filters = {}) {
  const data = read();
  let rows = data.expenses.map(e => ({ ...e, user: data.users.find(u => u.id === e.user_id) })).filter(e => e.user);
  if (filters.userId) rows = rows.filter(e => Number(e.user_id) === Number(filters.userId));
  if (filters.month) rows = rows.filter(e => String(e.expense_date).slice(0, 7) === filters.month);
  if (filters.from) rows = rows.filter(e => e.expense_date >= filters.from);
  if (filters.to) rows = rows.filter(e => e.expense_date <= filters.to);
  if (filters.status) rows = rows.filter(e => e.status === filters.status);
  if (filters.category) rows = rows.filter(e => e.category === filters.category);
  rows.sort((a, b) => String(b.expense_date).localeCompare(String(a.expense_date)) || b.id - a.id);
  return rows.map(e => ({ ...e, user_name: e.user.name, user_email: e.user.email, user: undefined }));
}

export async function findExpenseById(id) { return read().expenses.find(e => Number(e.id) === Number(id)) || null; }
export async function findExpenseByReceipt(filename) { return read().expenses.find(e => e.receipt_filename === filename) || null; }

export async function updateExpenseStatus(id, status) {
  const data = read();
  const expense = data.expenses.find(e => Number(e.id) === Number(id));
  if (!expense) return null;
  expense.status = status; expense.updated_at = new Date().toISOString(); write(data); return expense;
}

export async function deleteExpense(id) {
  const data = read();
  const index = data.expenses.findIndex(e => Number(e.id) === Number(id));
  if (index < 0) return false;
  data.expenses.splice(index, 1); write(data); return true;
}

export async function createReportLog(log) {
  const data = read();
  const saved = { id: data.counters.reportLogs++, created_at: new Date().toISOString(), ...log };
  data.reportLogs.push(saved); write(data); return saved;
}

export async function lastReportLog(month) {
  return read().reportLogs.filter(l => l.month === month).sort((a, b) => b.id - a.id)[0] || null;
}
