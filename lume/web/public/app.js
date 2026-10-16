import { initMascotMotion } from './motion.mjs';
import { initDemoReal } from './demo-real.mjs';

// O site mostra a gravação real do app (demo-real.mjs) e anima o mascote (motion.mjs). A simulação antiga, com cenários
// fictícios e um índice em porcentagem, saiu em 15/10/2026: ela não refletia mais o produto (o Lume não mostra
// porcentagem; mostra fontes, trechos e selos). Os arquivos scenarios.mjs e evidence.mjs ficam para os testes da rubrica.
initMascotMotion();
initDemoReal();
