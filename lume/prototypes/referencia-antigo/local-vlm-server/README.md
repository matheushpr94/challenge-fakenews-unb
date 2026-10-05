# Local VLM Server

Este é um servidor Python local que utiliza o `Qwen/Qwen2.5-VL-3B-Instruct` para analisar prints de notícias e extrair informações estruturadas (título, autor, fonte, corpo e possíveis afirmações).

## Como executar

1. Crie e ative um ambiente virtual:
   ```bash
   cd local-vlm-server
   python3 -m venv venv
   source venv/bin/activate
   ```

2. Instale as dependências:
   ```bash
   pip install -r requirements.txt
   ```

3. Inicie o servidor:
   ```bash
   python server.py
   ```
   *Nota: Na primeira execução, o modelo será baixado (~6GB). O servidor usará MPS (Metal Performance Shaders) automaticamente se você estiver num Mac com Apple Silicon.*

4. Verifique se o servidor está online:
   Abra `http://localhost:8000/health` no navegador.

5. Execute o aplicativo no emulador Android pelo Android Studio para testar a integração.
