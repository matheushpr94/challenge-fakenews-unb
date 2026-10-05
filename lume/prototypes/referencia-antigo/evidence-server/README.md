# Pesquisa de evidências do Lume (protótipo local)

Este servidor usa pesquisa web para explicar como fontes se relacionam com uma afirmação. O Android envia apenas o texto extraído do print. A chave fica no Mac, nunca no APK. A API pode gerar cobrança; consulte sua conta antes de usar.

No Terminal do Mac:

```bash
cd /Users/aluno1/AndroidStudioProjects/LumeOCRTest/evidence-server
read -s OPENAI_API_KEY; echo
export OPENAI_API_KEY
python3 server.py
```

Depois execute o app no emulador, selecione uma imagem e toque em **Buscar contexto e notícias**. O emulador alcança o servidor pelo endereço `10.0.2.2:8766`. Sem o servidor ou a chave, o app ainda mostra os links da Wikipédia e do Google Notícias. Para testar o servidor sem chave ou internet: `python3 -m unittest -v test_server.py`.

É um protótipo local: em um celular físico, o endereço do servidor e a implantação precisariam ser configurados. As fontes citadas são links de pesquisa; a resposta não determina a veracidade nem comprova que todo o conteúdo da página foi lido.
