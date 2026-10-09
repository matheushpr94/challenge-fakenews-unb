"""Etapa 8 do pipeline (ciclo 2): o pré-processamento do decisor, num objeto só.

Pergunta que responde: como transformar as entradas de `prepare.entradas_do_decisor()` em números que um modelo lê, de
um jeito que o estudo, o simulado, a prova e o app façam EXATAMENTE a mesma conta. Tudo que é aprendido (a mediana da
idade, a média e o desvio de cada número, a lista de valores de cada categoria) é aprendido só no estudo, no ajuste; o
simulado e a prova só passam pela transformação.

O que mudou do ciclo 1 (`ciclo-1/preprocessamento.py`): a candidata nova da etapa 7, `cosseno × idade`, o produto da
semelhança e da idade já preenchida e padronizada. Ela conta ao modelo que a mesma semelhança vale menos quando a fonte é
velha (no estudo, entre as muito parecidas, "mesmo" cai de 96 de cada 100 no mesmo dia para 43 acima de um mês).

Alternativa descartada: contas soltas no caderno (padronizar aqui, preencher ali). Funciona uma vez e diverge na
segunda: o caderno de treino ou o app refazem uma conta de um jeito um pouco diferente, e ninguém percebe.

Uso (o nome da variável começa com "preproc": é como a trava do pipeline reconhece pré-processamento):
    from preprocessamento import montar_preprocessador
    preprocessador = montar_preprocessador(com_regra=False, interacao=True)   # um objeto novo, sem aprender nada
    preprocessador.fit(estudo[preprocessador.entradas])                        # aprende SÓ com o estudo
    X_simulado = preprocessador.transform(simulado[preprocessador.entradas])
"""
from __future__ import annotations

import numpy as np
from sklearn.compose import ColumnTransformer
from sklearn.impute import SimpleImputer
from sklearn.pipeline import Pipeline
from sklearn.preprocessing import FunctionTransformer, OneHotEncoder, StandardScaler

# As mesmas listas do contrato (prepare.py, etapa 7); repetidas aqui para este módulo não depender do challenge.
NUMEROS = ["cosseno", "sobreposicao"]                 # já são números: só padronizar
IDADE = ["idade_dias"]                                # número com falta e cauda longa: log, preencher, padronizar
MARCAS = ["tem_idade", "comparou", "titulo_cortado"]  # já são 0 ou 1: passam como estão
CATEGORIAS = ["quem", "acao", "assunto", "quando", "buscador", "tipo_afirmacao"]
CANDIDATA = ["regra"]                                 # a resposta final da regra: entra só na versão com_regra


def _log_da_idade(x):
    """log(1 + dias), com os poucos -1 (fonte publicada um dia depois da matéria) juntados ao 0.

    np.clip(x, 0, None) troca o que for menor que 0 por 0; np.log1p(v) = log(1 + v), que aceita o zero.
    A falta (NaN) atravessa a conta intacta e é preenchida no passo seguinte.
    """
    return np.log1p(np.clip(x, 0, None))


def _produto(x):
    """Multiplica as duas colunas recebidas (cosseno padronizado e idade padronizada) e devolve uma coluna só.

    x[:, [0]] pega a primeira coluna mantendo o formato de tabela (n linhas, 1 coluna); o * multiplica linha a linha.
    Função com nome (e não lambda) para o objeto poder ser salvo e aberto de novo (joblib), como na etapa 15.
    """
    return x[:, [0]] * x[:, [1]]


def _nome_do_produto(transformador, nomes_de_entrada):
    """O nome da coluna nova, para `get_feature_names_out` (o scikit-learn pede uma função com estes dois argumentos)."""
    return np.array(["cosseno_x_idade"])


def _idade_tratada() -> Pipeline:
    """A fila de passos da idade: log, preencher a falta com a mediana do estudo, padronizar."""
    return Pipeline([
        # 1. a escala: log(1 + dias). FunctionTransformer embrulha uma função comum para ela virar um passo.
        ("log", FunctionTransformer(_log_da_idade, feature_names_out="one-to-one")),
        # 2. a falta vira a MEDIANA do estudo. Mediana, não média: a idade tem cauda longa (anos), e a média seria
        #    puxada pelos poucos casos extremos. A marca "tinha data ou não" já existe (tem_idade).
        ("preenche", SimpleImputer(strategy="median", add_indicator=False)),
        # 3. padroniza: subtrai a média e divide pelo desvio do estudo.
        ("padroniza", StandardScaler()),
    ])


def montar_preprocessador(com_regra: bool = False, interacao: bool = False, extras: tuple[str, ...] = ()) -> Pipeline:
    """Um objeto novo, ainda sem ter aprendido nada.

    `com_regra` liga a entrada candidata `regra`; `interacao` liga a candidata nova `cosseno × idade`; `extras` são
    colunas numéricas a mais, padronizadas como o cosseno (a nota do modelo de linguagem, se entrar na etapa 10).
    """
    categorias = CATEGORIAS + (CANDIDATA if com_regra else [])
    numeros = NUMEROS + list(extras)

    transformadores = [
        # (nome, objeto, colunas): cada grupo de colunas passa pelo seu tratamento.
        ("numeros", StandardScaler(), numeros),
        ("idade", _idade_tratada(), IDADE),
        # "passthrough": não mexe; as marcas já são 0 ou 1.
        ("marcas", "passthrough", MARCAS),
        # Uma coluna 0/1 por valor de cada categoria. handle_unknown="ignore": um valor que o estudo nunca viu vira
        # tudo zero, em vez de quebrar no simulado, na prova ou no app.
        ("categorias", OneHotEncoder(handle_unknown="ignore", sparse_output=False), categorias),
    ]
    if interacao:
        # A CANDIDATA NOVA: o cosseno e a idade passam de novo pelos MESMOS tratamentos (padronizar; log, preencher e
        # padronizar), aprendidos no mesmo estudo, e saem multiplicados. Uma fonte muito parecida (cosseno alto) e muito
        # velha (idade alta) dá um produto alto: é o sinal "parecida, mas velha" que o modelo não tinha.
        produto = Pipeline([
            ("lado_a_lado", ColumnTransformer([("cos", StandardScaler(), ["cosseno"]),
                                               ("idade", _idade_tratada(), IDADE)])),
            ("multiplica", FunctionTransformer(_produto, feature_names_out=_nome_do_produto)),
        ])
        transformadores.append(("interacao", produto, ["cosseno"] + IDADE))

    colunas = ColumnTransformer(
        transformers=transformadores,
        # remainder="drop": qualquer coluna fora das listas acima é descartada. É a segunda trava contra entrada
        # proibida (a primeira está no prepare.py): o que não foi listado não chega ao modelo.
        remainder="drop",
        verbose_feature_names_out=True,
    )
    preprocessador = Pipeline([("colunas", colunas)])
    # As colunas que o objeto espera receber, guardadas nele, para quem usa não precisar lembrar.
    preprocessador.entradas = numeros + IDADE + MARCAS + categorias
    return preprocessador
