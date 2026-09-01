# Trilha — planejamento e controle financeiro (Android nativo)

App de finanças pessoais em **Kotlin + Jetpack Compose**: perfis, categorias e
contas criadas por você, fluxo de caixa diário, gráficos que respondem ao toque e
um painel que se reordena conforme a sua situação. Todos os dados ficam no
aparelho, em arquivo privado do app. Nada é enviado para servidor nenhum.

## Como abrir e rodar

1. Android Studio → **Open** → selecione a pasta `Trilha/`.
2. Espere o Gradle sincronizar (ele baixa as dependências na primeira vez).
3. Ligue o celular por USB com depuração ativada, ou crie um emulador.
4. **Run ▶**.

Requisitos: Android Studio Ladybug ou mais recente, JDK 17, Android SDK 35.
`minSdk = 26` (Android 8.0) — necessário por causa da API `java.time`.

Para gerar o APK: **Build → Build Bundle(s)/APK(s) → Build APK(s)**.
Sai em `app/build/outputs/apk/debug/app-debug.apk`.

## Estrutura

```
app/src/main/java/br/com/trilha/
├── MainActivity.kt          ponto de entrada
├── data/
│   ├── Modelo.kt            entidades (Perfil, Renda, Fixo, Lancamento, Divida…)
│   └── Repositorio.kt       persistência em JSON no filesDir
├── domain/
│   ├── Financas.kt          toda a matemática — sem dependência de Android
│   ├── Prioridade.kt        relevância das seções e "ação do momento"
│   └── Formato.kt           moeda, porcentagem, datas em pt-BR
└── ui/
    ├── AppTrilha.kt         scaffold, abas animadas, diálogos
    ├── TrilhaViewModel.kt   estado e persistência
    ├── Tema.kt              paleta e tipografia
    ├── Comuns.kt            blocos, campos e linhas de valor
    ├── Animacoes.kt         números que contam, mostrador, barras
    ├── Graficos.kt          gráficos tocáveis e arrastáveis
    ├── PainelTela.kt        painel adaptativo
    ├── AgendaTela.kt        fluxo dia a dia, filtros, lançamentos, fechamento
    ├── DadosTela.kt         categorias, contas, renda, fixos, patrimônio
    │                        (contém também DividasTela)
    └── MetasTela.kt         metas, cenários, premissas
```

`domain/` é Kotlin puro, sem import de Android. Dá para testar em JVM comum e é
onde está o que realmente importa: o cálculo.

## O que o app calcula

**Fluxo de caixa diário.** Junta renda (pelo dia de recebimento), gastos fixos
(pelo dia de vencimento), parcelas de dívida e lançamentos avulsos numa linha do
tempo, e projeta o saldo dia a dia. É isso que revela o dia do mês em que a conta
fica negativa — informação que o total mensal esconde, porque no total tudo fecha.

**Fatura do cartão.** Compra feita depois do dia de fechamento cai na fatura
seguinte. `vencimentoFatura()` resolve isso, inclusive na virada de ano e quando o
dia de vencimento não existe no mês. Assim o gasto aparece no caixa no dia em que
o dinheiro sai de verdade, não no dia da compra.

**Quitação de dívidas.** Simulação mês a mês comparando avalanche (maior juro
primeiro) e bola de neve (menor saldo primeiro), com prazo e juros totais de cada
uma. Quando a diferença de custo é pequena, o app recomenda bola de neve — o
ganho psicológico de quitar um contrato cedo supera alguns reais de juro. Se as
parcelas não cobrem os juros, ele para e diz que o caso é de renegociação.

**Metas.** Aporte necessário pela fórmula de anuidade, prazo real com o aporte
informado, três cenários de rendimento e o efeito da inflação sobre o valor alvo.
Se o aporte planejado for maior que a sobra mensal, o app acusa que o plano não
cabe no orçamento.

**Pontuação 0–100.** Sobra 25, reserva 20, endividamento 20, patrimônio investido
15, supérfluos 10, disciplina de registro 10. Os pesos são uma escolha explícita:
caixa e reserva pesam mais porque são o que impede uma dívida nova. Perfil sem
nenhum dado marca 0 — dar 20 pontos por "não ter dívidas" a quem simplesmente não
cadastrou nada seria mentira.

## O que torna o app dinâmico

**Categorias e contas são suas.** Você cria, renomeia, escolhe o ícone, define a
classe e um teto mensal opcional. O mesmo vale para contas e cartões: cada cartão
tem fechamento e vencimento próprios, então dois cartões geram duas faturas em
datas diferentes. Apagar uma categoria reatribui os lançamentos em vez de perdê-los.

**O painel se reordena.** `painelAdaptativo` pontua cada seção pela situação
atual e ordena por relevância. Com dívida a 12% ao mês, "Dívidas" abre o painel;
com reserva de 15 meses e caixa positivo, quem abre é "Metas". No topo fica a
*ação do momento*: uma única frase sobre o que fazer agora, com botão que leva
direto à aba certa.

**Os gráficos respondem ao toque.** O saldo diário é arrastável — o dedo percorre
os dias e o valor acompanha. As barras de evolução mensal são selecionáveis. No
gráfico das metas dá para trocar de cenário por chip e arrastar para ver mês a mês
quanto falta. A barra de composição da renda filtra por classe ao ser tocada.

**A interface anima.** Números percorrem a distância entre o valor antigo e o
novo, para você perceber a direção da mudança. O mostrador da pontuação cresce e
muda de cor por faixa. A troca de abas e de meses desliza no sentido da navegação.

## Premissas de rendimento

Vêm preenchidas com conservador 7%, base 11,5%, otimista 15% e inflação 5% ao ano.
A referência é o cenário de 31/08/2026 — Selic em 14% a.a. desde a reunião do Copom
de 05/08/2026, com o Focus projetando 13,75% para o fim de 2026 e 12% para 2027.
Como a projeção é de queda, usar o CDI de hoje para os próximos anos
superestimaria o resultado. Tudo editável na aba Metas.

Nenhuma dessas taxas é garantia. O app organiza números e mostra cenários; não é
recomendação de investimento nem substitui um profissional certificado.

## Estado da verificação

- `domain/` foi **compilado e executado** com o compilador Kotlin 2.0.21, em dez
  blocos de teste: migração de dados antigos (não duplica ao rodar de novo),
  dois cartões com fechamentos diferentes gerando faturas independentes, fluxo
  diário, orçamento por categoria com e sem teto manual, comparação de meses,
  reordenação do painel em três situações opostas, próximos vencimentos com data
  fixa, perfil vazio, dívidas e metas. Os resultados batem com uma implementação
  de referência independente.
- Dois defeitos foram encontrados nesses testes e corrigidos: o "dia do menor
  saldo" saía como dia 0 quando o saldo nunca caía abaixo do inicial, e o perfil
  vazio marcava 20 pontos por não ter dívidas cadastradas.
- A camada Compose passou por verificação de sintaxe, mas **não foi compilada
  contra o SDK do Android** — não havia SDK no ambiente onde o projeto foi gerado.
  Se algo não compilar de primeira, será nessa camada, e provavelmente por versão
  de dependência. O `domain/` não deve ser afetado.

## Persistência

Documento JSON único em `filesDir/trilha.json`, via kotlinx-serialization. O
volume é pequeno — um documento por aparelho — então um banco relacional traria
complexidade sem ganho. Se o app crescer para histórico longo com consultas por
período, o caminho é migrar `Repositorio` para Room mantendo `domain/` intacto.

Dados gravados na versão anterior do app continuam funcionando: `migrar()` cria
contas e categorias a partir do que existia (`forma` virou conta, o nome de cada
gasto virou categoria preservando a classe) e roda uma vez só.

Backup e restauração por texto JSON no menu de ajustes.
