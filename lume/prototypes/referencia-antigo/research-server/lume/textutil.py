"""Normalização, tokens e radicais simples para português (sem modelos)."""
import re
import unicodedata

WORD_RE = re.compile(r"\w[\w'’\-]*", re.UNICODE)

STOPWORDS = set("""
a à ao aos as às o os um uma uns umas de da do das dos dum duma em no na nos nas num numa
por pelo pela pelos pelas para pra pro com sob sobre entre até apos após ante contra desde e ou mas
que se como quando onde quem qual quais cujo cuja porque pois já tambem também só mais menos muito
muita muitos muitas pouco tão tanto isso isto aquilo esse essa esses essas este esta estes estas aquele
aquela aqueles aquelas ele ela eles elas eu tu voce você vocês voces nós nos vos lhe lhes me te seu sua
seus suas meu minha nosso nossa dele dela deles delas foi ser é e são sao era eram será sera seria sido
sendo está esta estão estao estava estavam estar esteve tem têm tinha ter tido há ha havia haver vai vão
vao ia fazer faz fez feito diz disse dizem dizer afirma afirmou afirmam declarou falou segundo sobre
aí ai lá la aqui ali bem ainda sim então entao todo toda todos todas outro outra outros outras mesmo
mesma cada qualquer algum alguma alguns algumas nada tudo ninguém algo coisa verdade
the of and to in on for is are was were be by with at from this that it as an or
""".split())

NEGATIONS = {"não", "nao", "nunca", "jamais", "nem", "nenhum", "nenhuma", "negou", "nega", "negam",
             "desmente", "desmentiu", "desmentem", "not", "never", "no"}

MONTHS = {"janeiro": 1, "fevereiro": 2, "março": 3, "marco": 3, "abril": 4, "maio": 5, "junho": 6,
          "julho": 7, "agosto": 8, "setembro": 9, "outubro": 10, "novembro": 11, "dezembro": 12}
WEEKDAYS = {"segunda", "terça", "terca", "quarta", "quinta", "sexta", "sábado", "sabado", "domingo",
            "segunda-feira", "terça-feira", "quarta-feira", "quinta-feira", "sexta-feira"}

_SUFFIXES = ["amentos", "imentos", "amento", "imento", "acoes", "icoes", "acao", "icao", "mente",
             "aram", "eram", "iram", "ando", "endo", "indo", "ados", "idos", "adas", "idas", "ado",
             "ido", "ada", "ida", "ou", "am", "em", "ar", "er", "ir", "os", "as", "es", "s", "a", "o",
             "e"]


def strip_accents(s):
    return "".join(c for c in unicodedata.normalize("NFKD", s) if not unicodedata.combining(c))


def norm(s):
    """Minúsculas, sem acentos, apenas letras/dígitos separados por espaço."""
    s = strip_accents(s or "").lower()
    s = re.sub(r"(?<=\d)[.,](?=\d)", "\u0000", s)  # preserva 5,2 e 1.000 como um token
    s = re.sub(r"[^a-z0-9\u0000]+", " ", s).replace("\u0000", ",")
    return " ".join(s.split())


def tokens(s):
    return norm(s).split()


def stem(word):
    w = norm(word).replace(" ", "")
    for suf in _SUFFIXES:
        if w.endswith(suf) and len(w) - len(suf) >= 4:
            w = w[: -len(suf)]
            break
    return w[:6]


def stems(s):
    return {stem(t) for t in tokens(s) if len(t) >= 3}


def number_key(raw):
    """'R$ 1.500,00' -> '1500,00'; '5,2%' -> '5,2'."""
    digits = re.sub(r"[^\d.,]", "", raw)
    digits = re.sub(r"\.(?=\d{3}(\D|$))", "", digits)
    return digits.strip(".,")


def numbers_in(text):
    keys = set()
    for m in re.finditer(r"\d+(?:[.,]\d+)*", text or ""):
        keys.add(number_key(m.group(0)))
    return keys


def jaccard(a, b):
    if not a or not b:
        return 0.0
    return len(a & b) / len(a | b)


def has_negation(text):
    return bool(set(tokens(text)) & NEG_NORM)


STOP_NORM = {norm(w) for w in STOPWORDS}
NEG_NORM = {norm(w) for w in NEGATIONS} - {"no"}  # "no" em português é preposição
