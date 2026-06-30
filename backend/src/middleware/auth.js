import jwt from 'jsonwebtoken';
import { findUserById } from '../config/store.js';

export async function auth(req, res, next) {
  try {
    const header = req.headers.authorization || '';
    const token = header.startsWith('Bearer ') ? header.slice(7) : null;
    if (!token) return res.status(401).json({ message: 'Token não informado.' });

    const payload = jwt.verify(token, process.env.JWT_SECRET);
    const user = await findUserById(payload.id);
    if (!user) return res.status(401).json({ message: 'Usuário inválido.' });

    req.user = { id: user.id, name: user.name, email: user.email, role: user.role || 'user' };
    next();
  } catch {
    return res.status(401).json({ message: 'Sessão inválida ou expirada.' });
  }
}

export function adminOnly(req, res, next) {
  if (req.user?.role !== 'admin') return res.status(403).json({ message: 'Acesso restrito ao administrador.' });
  next();
}
