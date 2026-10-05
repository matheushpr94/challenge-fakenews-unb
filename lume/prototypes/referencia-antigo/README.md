# Referência: versão anterior do Lume

Código e documentos da fase anterior do projeto (antes da pesquisa nativa no Android + Ollama).
**Nada aqui é usado pelo app atual** (`../ocr-native-android`). Fica como registro.

| Pasta | O que é | Situação |
|---|---|---|
| `research-server/` | protótipo web de pesquisa de contexto (Python, só biblioteca padrão, porta 8770) | origem da lógica portada para o app; ainda executável (`python3 server.py`) |
| `evidence-server/` | servidor de evidências com a API da OpenAI (a chave ficava no Mac, nunca no APK) | descontinuado; pode gerar cobrança |
| `local-vlm-server/` | servidor de visão com Qwen2.5-VL-3B para ler prints | descontinuado; o app lê com ML Kit no aparelho |
| `ml-training/` | baseline com LIAR2 que prevê 6 classes de veracidade (acurácia de teste ~31%) | descontinuado; contraria a regra de não dar veredito. Os modelos treinados (`artifacts/`) não estão no Git |
| `docs-frontend/` | duas imagens da interface antiga | referência visual |
| `RESEARCH-INTEGRATION.md` | notas da integração antiga | histórico |

Ambientes virtuais (`venv`, `.venv`) e logs não foram copiados: recriam-se com `requirements.txt`.
