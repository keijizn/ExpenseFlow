import 'dotenv/config';
import express from 'express';
import cors from 'cors';
import cron from 'node-cron';
import { migrate } from './config/migrate.js';
import authRoutes from './routes/authRoutes.js';
import expenseRoutes from './routes/expenseRoutes.js';
import reportRoutes from './routes/reportRoutes.js';
import { sendMonthlyReport } from './services/reportService.js';

await migrate();

const app = express();
app.use(cors({ origin: process.env.FRONTEND_URL || 'http://localhost:5173' }));
app.use(express.json());

app.get('/health', (_, res) => res.json({ ok: true }));
app.use('/api/auth', authRoutes);
app.use('/api/expenses', expenseRoutes);
app.use('/api/reports', reportRoutes);

if (process.env.ENABLE_CRON !== 'false') {
  cron.schedule('0 8 1 * *', async () => {
    const now = new Date();
    const previous = new Date(now.getFullYear(), now.getMonth() - 1, 1);
    const month = previous.toISOString().slice(0, 7);
    try { await sendMonthlyReport(month); }
    catch (err) { console.error('Erro no envio automático mensal:', err.message); }
  }, { timezone: 'America/Sao_Paulo' });
}

const port = Number(process.env.PORT || 3001);
app.listen(port, () => console.log(`API rodando em http://localhost:${port}`));
