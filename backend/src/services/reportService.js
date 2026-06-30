import nodemailer from 'nodemailer';
import { createReportLog, lastReportLog, listExpenses } from '../config/store.js';

function brl(cents) { return (cents / 100).toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' }); }
function escapeHtml(value) { return String(value ?? '').replace(/[&<>"]/g, s => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;' }[s])); }

export async function getMonthlyReportData(targetMonth, filters = {}) {
  const rows = (await listExpenses({ month: targetMonth, ...filters })).sort((a, b) => a.user_name.localeCompare(b.user_name) || a.expense_date.localeCompare(b.expense_date));
  const totalCents = rows.reduce((sum, r) => sum + r.amount_cents, 0);
  const byUser = rows.reduce((acc, r) => { acc[r.user_name] = (acc[r.user_name] || 0) + r.amount_cents; return acc; }, {});
  const byCategory = rows.reduce((acc, r) => { acc[r.category] = (acc[r.category] || 0) + r.amount_cents; return acc; }, {});
  const log = await lastReportLog(targetMonth);
  return { month: targetMonth, totalCents, total: brl(totalCents), count: rows.length, byUser, byCategory, rows, lastSentAt: log?.created_at || null, reportTo: process.env.REPORT_TO || '' };
}

export function buildReportHtml(data) {
  const lines = data.rows.map(r => `<tr><td>${escapeHtml(r.expense_date)}</td><td>${escapeHtml(r.user_name)}</td><td>${escapeHtml(r.category)}</td><td>${escapeHtml(r.status)}</td><td>${escapeHtml(r.description)}</td><td style="text-align:right">${brl(r.amount_cents)}</td><td>${escapeHtml(r.receipt_original_name || '-')}</td></tr>`).join('');
  const userSummary = Object.entries(data.byUser).map(([name, cents]) => `<li>${escapeHtml(name)}: <strong>${brl(cents)}</strong></li>`).join('');
  const categorySummary = Object.entries(data.byCategory).map(([name, cents]) => `<li>${escapeHtml(name)}: <strong>${brl(cents)}</strong></li>`).join('');
  return `<div style="font-family:Arial,sans-serif;color:#111827"><h2>Resumo de reembolsos: ${escapeHtml(data.month)}</h2><p>Total lançado: <strong>${data.total}</strong></p><h3>Por usuário</h3><ul>${userSummary || '<li>Nenhum lançamento no mês.</li>'}</ul><h3>Por categoria</h3><ul>${categorySummary || '<li>Nenhum lançamento no mês.</li>'}</ul><table width="100%" cellpadding="8" cellspacing="0" border="1" style="border-collapse:collapse;border-color:#e5e7eb"><thead><tr><th>Data</th><th>Usuário</th><th>Categoria</th><th>Status</th><th>Descrição</th><th>Valor</th><th>Comprovante</th></tr></thead><tbody>${lines || '<tr><td colspan="7">Nenhum lançamento encontrado.</td></tr>'}</tbody></table></div>`;
}

function getTransporter() {
  return nodemailer.createTransport({
    host: process.env.SMTP_HOST,
    port: Number(process.env.SMTP_PORT || 465),
    secure: String(process.env.SMTP_SECURE) === 'true',
    auth: { user: process.env.SMTP_USER, pass: process.env.SMTP_PASS }
  });
}

export async function sendMonthlyReport(targetMonth, sentByUserId = null) {
  const data = await getMonthlyReportData(targetMonth);
  const transporter = getTransporter();
  await transporter.sendMail({ from: process.env.REPORT_FROM || process.env.SMTP_USER, to: process.env.REPORT_TO, subject: `Resumo de reembolsos - ${targetMonth}`, html: buildReportHtml(data) });
  await createReportLog({ month: targetMonth, sent_by_user_id: sentByUserId, sent_to: process.env.REPORT_TO || '', total_cents: data.totalCents, expense_count: data.count });
  return { month: targetMonth, total: data.total, count: data.count, sentTo: process.env.REPORT_TO };
}

export async function emailReportTo({ targetMonth, recipientEmail, sentByUserId = null, filters = {} }) {
  const data = await getMonthlyReportData(targetMonth, filters);
  const transporter = getTransporter();
  await transporter.sendMail({
    from: process.env.REPORT_FROM || process.env.SMTP_USER,
    to: recipientEmail,
    subject: `Relatório de reembolsos - ${targetMonth}`,
    html: buildReportHtml(data),
    attachments: [{
      filename: `reembolsos-${targetMonth}.csv`,
      content: '\ufeff' + toCsv(data),
      contentType: 'text/csv; charset=utf-8'
    }]
  });
  await createReportLog({ month: targetMonth, sent_by_user_id: sentByUserId, sent_to: recipientEmail, total_cents: data.totalCents, expense_count: data.count });
  return { month: targetMonth, total: data.total, count: data.count, sentTo: recipientEmail };
}

export function toCsv(data) {
  const header = ['Data', 'Usuário', 'E-mail', 'Categoria', 'Status', 'Descrição', 'Valor', 'Comprovante'];
  const escapeCsv = v => `"${String(v ?? '').replace(/"/g, '""')}"`;
  const body = data.rows.map(r => [r.expense_date, r.user_name, r.user_email, r.category, r.status, r.description, (r.amount_cents / 100).toFixed(2).replace('.', ','), r.receipt_original_name || ''].map(escapeCsv).join(';'));
  return [header.map(escapeCsv).join(';'), ...body].join('\n');
}
