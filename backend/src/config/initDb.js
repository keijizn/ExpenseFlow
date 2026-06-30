import { migrate } from './migrate.js';
await migrate();
console.log('Banco JSON inicializado com sucesso.');
process.exit(0);
