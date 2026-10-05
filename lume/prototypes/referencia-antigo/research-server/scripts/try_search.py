"""Executa buscas reais pelo terminal: python3 scripts/try_search.py "texto" ["outro texto"]"""
import logging, json, sys
import os; sys_path_root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
logging.basicConfig(level=logging.INFO, format="%(message)s")
sys.path.insert(0, sys_path_root)
from lume.service import LumeService
svc=LumeService()
for q in sys.argv[1:]:
    r=svc.search(q)
    print('\n###', q, '\n', r['status'], '|', r['mensagem'], r['duracao_ms'], r['avisos'])
    si=r.get('sintese',{})
    print('  SINTESE:', si.get('situacao'), '|', si.get('detalhe'))
    for e in si.get('explicacao',[]): print('    -', e['texto'][:220])
    for e in si.get('evidencias',[])[:8]: print('    ev', e['relacao'], e['texto_valor'], e['qualificadores'], e['veiculo'], e['conteudo'], '|', e['trecho'][:110])
    for k in ('responde','direto','anterior','contexto','contexto_geral'):
        for g in r['resultados'][k]:
            print(' ',k, '|', g['veiculo'], '|', (g['data'] or '')[:10], '|', g['conteudo'], '|', g['titulo'][:85], '| rep', len(g['republicacoes']), g['alertas'][:2], g['trechos_pagina'][:1] and g['trechos_pagina'][0][:120])
    print('  sem resposta:', [d['detalhe'] for d in r['achados']['detalhes_sem_resposta']], '| obs:', r['achados']['observacoes'])
    print('  sugestoes:', [s['texto'][:60] for s in r['sugestoes']])
    print('  descartados:', r['descartados']['quantidade'], [ (e['titulo'][:50], e['motivo']) for e in r['descartados']['exemplos'][:4]])
