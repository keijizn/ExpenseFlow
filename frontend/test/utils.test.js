import { test } from 'node:test';
import assert from 'node:assert/strict';
import { localDate, emptyState, csvCell } from '../src/utils.js';

test('data civil usa o dia local mesmo às 23h', () => {
  process.env.TZ = 'America/Sao_Paulo';
  const date = new Date(2026, 9, 6, 23, 30);
  assert.equal(localDate(date), '2026-10-06');
});
test('troca de sessão recebe coleções novas', () => {
  const previous = emptyState(); previous.transactions.push({ id: 1 });
  assert.deepEqual(emptyState().transactions, []);
});
test('CSV escapa aspas e neutraliza fórmulas', () => {
  assert.equal(csvCell('Loja "A"'), '"Loja ""A"""');
  assert.equal(csvCell('=SUM(1;2)'), '"\'=SUM(1;2)"');
});
