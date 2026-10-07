import React from 'react';
import { BarChart, Bar, PieChart, Pie, Cell, ResponsiveContainer, Tooltip, LineChart, Line } from 'recharts';
const money = value => new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(value);

export function ExpensePie({ data }) {
  return <ResponsiveContainer width="100%" height={220}><PieChart><Pie data={data} dataKey="value" innerRadius={55} outerRadius={90}>{data.map((item, index) => <Cell key={index} fill={item.color || '#a3e635'} />)}</Pie><Tooltip formatter={money} /></PieChart></ResponsiveContainer>;
}
export function IncomeExpenseChart({ data }) {
  return <ResponsiveContainer width="100%" height={240}><BarChart data={data}><Bar dataKey="entradas" fill="#22c55e" radius={[8,8,0,0]} /><Bar dataKey="saidas" fill="#ef4444" radius={[8,8,0,0]} /><Tooltip formatter={money} /></BarChart></ResponsiveContainer>;
}
export function AnnualChart({ data }) {
  return <ResponsiveContainer width="100%" height={320}><LineChart data={data}><Line dataKey="saldo" stroke="#38bdf8" strokeWidth={3} /><Line dataKey="entradas" stroke="#22c55e" strokeWidth={2} /><Line dataKey="saidas" stroke="#ef4444" strokeWidth={2} /><Tooltip formatter={money} /></LineChart></ResponsiveContainer>;
}
