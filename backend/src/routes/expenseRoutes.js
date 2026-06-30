import express from 'express';
import multer from 'multer';
import path from 'path';
import fs from 'fs';
import { auth, adminOnly } from '../middleware/auth.js';
import { createExpense, deleteExpense, findExpenseById, findExpenseByReceipt, listExpenses, updateExpenseStatus } from '../config/store.js';

const router = express.Router();
const uploadDir = path.resolve('src/uploads');
if (!fs.existsSync(uploadDir)) fs.mkdirSync(uploadDir, { recursive: true });

const CATEGORIES = ['Cartório', 'Transporte', 'Alimentação', 'Material', 'Serviço', 'Outros'];
const STATUSES = ['Pendente', 'Enviado para reembolso', 'Reembolsado', 'Recusado'];

const storage = multer.diskStorage({
  destination: (_, __, cb) => cb(null, uploadDir),
  filename: (_, file, cb) => cb(null, `${Date.now()}-${Math.round(Math.random() * 1e9)}${path.extname(file.originalname).toLowerCase()}`)
});
const upload = multer({
  storage,
  limits: { fileSize: 8 * 1024 * 1024 },
  fileFilter: (_, file, cb) => {
    const allowed = ['application/pdf', 'image/jpeg', 'image/png', 'image/webp'];
    if (!allowed.includes(file.mimetype)) return cb(new Error('Arquivo deve ser PDF ou imagem.'));
    cb(null, true);
  }
});

function parseAmountToCents(value) {
  if (value === undefined || value === null || value === '') return null;
  const n = Number(String(value).replace(/\./g, '').replace(',', '.'));
  if (!Number.isFinite(n) || n <= 0) return null;
  return Math.round(n * 100);
}
function getFilters(req) {
  return {
    userId: req.user.role === 'admin' ? req.query.userId || null : req.user.id,
    month: req.query.month || null,
    from: req.query.from || null,
    to: req.query.to || null,
    status: req.query.status || null,
    category: req.query.category || null
  };
}
function publicExpense(e) { return { ...e, amount: e.amount_cents / 100 }; }

router.get('/options', auth, (_, res) => res.json({ categories: CATEGORIES, statuses: STATUSES }));

router.get('/', auth, async (req, res) => {
  const rows = await listExpenses(getFilters(req));
  res.json(rows.map(publicExpense));
});

router.get('/dashboard', auth, async (req, res) => {
  const rows = await listExpenses(getFilters(req));
  const totalCents = rows.reduce((s, r) => s + r.amount_cents, 0);
  const byStatus = rows.reduce((acc, r) => ({ ...acc, [r.status]: (acc[r.status] || 0) + 1 }), {});
  const last = rows[0] || null;
  res.json({ total: totalCents / 100, count: rows.length, byStatus, last: last ? publicExpense(last) : null });
});

router.post('/', auth, upload.single('receipt'), async (req, res) => {
  try {
    const { amount, description, expenseDate, category } = req.body;
    const amountCents = parseAmountToCents(amount);
    const chosenCategory = CATEGORIES.includes(category) ? category : 'Outros';
    if (!amountCents || !description || !expenseDate) return res.status(400).json({ message: 'Valor, descrição e data são obrigatórios.' });
    const saved = await createExpense({
      user_id: req.user.id,
      amount_cents: amountCents,
      description: description.trim(),
      expense_date: expenseDate,
      category: chosenCategory,
      receipt_filename: req.file?.filename || null,
      receipt_original_name: req.file?.originalname || null
    });
    res.status(201).json(publicExpense(saved));
  } catch (err) {
    res.status(500).json({ message: err.message || 'Erro ao salvar despesa.' });
  }
});

router.patch('/:id/status', auth, adminOnly, async (req, res) => {
  const { status } = req.body;
  if (!STATUSES.includes(status)) return res.status(400).json({ message: 'Status inválido.' });
  const updated = await updateExpenseStatus(req.params.id, status);
  if (!updated) return res.status(404).json({ message: 'Despesa não encontrada.' });
  res.json(publicExpense(updated));
});

router.delete('/:id', auth, async (req, res) => {
  const expense = await findExpenseById(req.params.id);
  if (!expense || (req.user.role !== 'admin' && expense.user_id !== req.user.id)) return res.status(404).json({ message: 'Despesa não encontrada.' });
  if (expense.receipt_filename) {
    const filePath = path.join(uploadDir, expense.receipt_filename);
    if (fs.existsSync(filePath)) fs.unlinkSync(filePath);
  }
  await deleteExpense(req.params.id);
  res.status(204).end();
});

router.get('/receipt/:filename', auth, async (req, res) => {
  const expense = await findExpenseByReceipt(req.params.filename);
  if (!expense || (req.user.role !== 'admin' && expense.user_id !== req.user.id)) return res.status(404).json({ message: 'Arquivo não encontrado.' });
  const filePath = path.join(uploadDir, req.params.filename);
  if (!fs.existsSync(filePath)) return res.status(404).json({ message: 'Arquivo não encontrado.' });
  res.sendFile(filePath);
});

export default router;
