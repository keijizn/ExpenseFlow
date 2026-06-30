import express from 'express';
import bcrypt from 'bcryptjs';
import jwt from 'jsonwebtoken';
import { createUser, findUserByEmail } from '../config/store.js';
import { auth } from '../middleware/auth.js';

const router = express.Router();

function isAdminEmail(email) {
  return String(process.env.ADMIN_EMAILS || '').split(',').map(e => e.trim().toLowerCase()).filter(Boolean).includes(String(email || '').toLowerCase());
}
function publicUser(user) { return { id: user.id, name: user.name, email: user.email, role: user.role || 'user' }; }

router.post('/register', async (req, res) => {
  try {
    const { name, email, password } = req.body;
    if (!name || !email || !password) return res.status(400).json({ message: 'Nome, e-mail e senha são obrigatórios.' });
    if (password.length < 6) return res.status(400).json({ message: 'A senha deve ter pelo menos 6 caracteres.' });
    const cleanEmail = email.toLowerCase().trim();
    const existing = await findUserByEmail(cleanEmail);
    if (existing) return res.status(409).json({ message: 'E-mail já cadastrado.' });
    const password_hash = await bcrypt.hash(password, 10);
    const role = isAdminEmail(cleanEmail) ? 'admin' : 'user';
    const saved = await createUser({ name: name.trim(), email: cleanEmail, password_hash, role });
    const token = jwt.sign({ id: saved.id }, process.env.JWT_SECRET, { expiresIn: '7d' });
    return res.status(201).json({ token, user: publicUser(saved) });
  } catch {
    return res.status(500).json({ message: 'Erro ao cadastrar usuário.' });
  }
});

router.post('/login', async (req, res) => {
  try {
    const { email, password } = req.body;
    if (!email || !password) return res.status(400).json({ message: 'E-mail e senha são obrigatórios.' });
    const user = await findUserByEmail(email.toLowerCase().trim());
    if (!user) return res.status(401).json({ message: 'Credenciais inválidas.' });
    const ok = await bcrypt.compare(password, user.password_hash);
    if (!ok) return res.status(401).json({ message: 'Credenciais inválidas.' });
    const token = jwt.sign({ id: user.id }, process.env.JWT_SECRET, { expiresIn: '7d' });
    return res.json({ token, user: publicUser(user) });
  } catch {
    return res.status(500).json({ message: 'Erro ao fazer login.' });
  }
});

router.get('/me', auth, (req, res) => res.json({ user: req.user }));
export default router;
