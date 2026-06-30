import express from 'express';
import { auth, adminOnly } from '../middleware/auth.js';
import { emailReportTo, getMonthlyReportData, sendMonthlyReport, toCsv } from '../services/reportService.js';

const router = express.Router();

router.get('/preview', auth, adminOnly, async (req, res) => {
  try {
    const month = req.query.month || new Date().toISOString().slice(0, 7);
    const data = await getMonthlyReportData(month);
    res.json({
      month: data.month,
      total: data.total,
      count: data.count,
      byUser: Object.fromEntries(Object.entries(data.byUser).map(([k, v]) => [k, v / 100])),
      byCategory: Object.fromEntries(Object.entries(data.byCategory).map(([k, v]) => [k, v / 100])),
      rows: data.rows.map(r => ({ ...r, amount: r.amount_cents / 100 })),
      lastSentAt: data.lastSentAt,
      reportTo: data.reportTo
    });
  } catch {
    res.status(500).json({ message: 'Erro ao gerar prévia.' });
  }
});

router.get('/export.csv', auth, adminOnly, async (req, res) => {
  try {
    const month = req.query.month || new Date().toISOString().slice(0, 7);
    const data = await getMonthlyReportData(month);
    res.setHeader('Content-Type', 'text/csv; charset=utf-8');
    res.setHeader('Content-Disposition', `attachment; filename="reembolsos-${month}.csv"`);
    res.send('\ufeff' + toCsv(data));
  } catch {
    res.status(500).json({ message: 'Erro ao exportar relatório.' });
  }
});

router.post('/send', auth, adminOnly, async (req, res) => {
  try {
    const month = req.body.month || new Date().toISOString().slice(0, 7);
    const result = await sendMonthlyReport(month, req.user.id);
    res.json({ message: 'Relatório enviado com sucesso.', result });
  } catch (err) {
    res.status(500).json({ message: 'Erro ao enviar relatório. Confira as variáveis SMTP no .env.' });
  }
});

router.post('/export-email', auth, async (req, res) => {
  try {
    const { email, month, status, category } = req.body;
    const cleanEmail = String(email || '').trim();
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(cleanEmail)) {
      return res.status(400).json({ message: 'Informe um e-mail válido para receber o relatório.' });
    }

    const targetMonth = month || new Date().toISOString().slice(0, 7);
    const filters = {
      userId: req.user.role === 'admin' ? null : req.user.id,
      status: status || null,
      category: category || null
    };

    const result = await emailReportTo({
      targetMonth,
      recipientEmail: cleanEmail,
      sentByUserId: req.user.id,
      filters
    });

    res.json({ message: `Relatório enviado para ${cleanEmail}.`, result });
  } catch (err) {
    res.status(500).json({ message: 'Erro ao enviar relatório. Confira as variáveis SMTP no .env.' });
  }
});

export default router;
