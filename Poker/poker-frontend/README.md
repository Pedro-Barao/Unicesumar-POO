# Poker Hold'em — Front-end

Front-end em **HTML, CSS e JavaScript puro** (sem build, sem dependências).

## Como rodar
Basta abrir `index.html` no navegador. Para evitar restrições do navegador com `file://`,
pode-se usar um servidor simples:

```bash
npx serve .          # ou
python -m http.server 8080
```

## Estrutura
```
index.html        Página 1 — Lobby: lista, filtra e cria salas
mesa.html         Página 2 — Mesa de jogo (mesa.html?sala=ID)
css/base.css      Estilos compartilhados (cores, botões, cartas, modal)
css/lobby.css     Estilos do lobby
css/mesa.css      Estilos da mesa
js/api.js         Camada de comunicação com o back-end (hoje SIMULADA)
js/lobby.js       Lógica da página de salas
js/mesa.js        Desenha a mesa a partir do "estado" e envia ações
```

## Back-end simulado
`js/api.js` traz um mock completo: 8 salas fictícias e uma mesa com bots que
jogam de verdade (blinds, apostas, flop/turn/river, showdown com avaliador de mãos).
Simplificações do mock: sem side pots e recompra automática quando alguém quebra.

## Integrando com o back-end real
1. Em `js/api.js`, troque `USAR_MOCK = true` para `false`.
2. Ajuste `URL_API` e `URL_WS`.
3. O servidor deve fornecer:

| Função | Back-end esperado |
|---|---|
| `listarSalas()` | `GET /api/salas` → lista de salas |
| `obterSala(id)` | `GET /api/salas/:id` → uma sala |
| `criarSala(dados)` | `POST /api/salas` → sala criada |
| `conectarMesa(...)` | WebSocket `ws://.../ws?sala=ID&nome=APELIDO` |

### Formato de uma sala
```json
{ "id": "copacabana", "nome": "Copacabana", "smallBlind": 10, "bigBlind": 20,
  "buyIn": 2000, "maxJogadores": 8, "jogadores": 6, "emJogo": true, "privada": false }
```

### Mensagens do WebSocket
Cliente → servidor:
```json
{ "tipo": "acao", "acao": "desistir|passar|pagar|aumentar|allin", "valor": 120 }
{ "tipo": "chat", "texto": "boa sorte!" }
```
(`valor` só em `aumentar`, e é o valor **total** apostado na rodada.)

Servidor → cliente:
```json
{ "tipo": "estado", "estado": { ...ver abaixo... } }
{ "tipo": "log",  "texto": "Ana: pagou 20" }
{ "tipo": "chat", "autor": "Ana", "texto": "gg" }
```

### Objeto `estado` da mesa
```json
{
  "sala": { "id": "copacabana", "nome": "Copacabana", "smallBlind": 10, "bigBlind": 20 },
  "fase": "aguardando | pre-flop | flop | turn | river | showdown",
  "pote": 300,                 // fichas já recolhidas (sem as apostas da rodada)
  "cartasMesa": ["Ah", "Td", "7c"],
  "apostaAtual": 40,           // maior aposta da rodada
  "minRaise": 20,              // aumento mínimo
  "dealer": 2,                 // assento do dealer
  "vezDe": 0,                  // assento que deve agir (-1 = ninguém)
  "prazoAcao": 1730000000000,  // timestamp (ms) limite da jogada
  "tempoAcao": 20000,          // duração total do tempo de jogada (ms)
  "meuAssento": 0,
  "resultado": null,           // texto exibido no fim da mão
  "assentos": [
    { "assento": 0, "nome": "Pedro", "fichas": 1960, "aposta": 40,
      "cartas": ["Ks", "Kh"], "desistiu": false, "allin": false,
      "ultimaAcao": "Pagou 40", "voce": true, "vencedor": false, "maoNome": null },
    { "assento": 1, "nome": "Ana", "fichas": 2000, "aposta": 0,
      "cartas": ["??", "??"], "desistiu": false, "allin": false,
      "ultimaAcao": null, "voce": false, "vencedor": false, "maoNome": null },
    { "assento": 2, "vazio": true }
  ]
}
```
Cartas: valor (`2-9`, `T`, `J`, `Q`, `K`, `A`) + naipe (`s` espadas, `h` copas,
`d` ouros, `c` paus). Cartas ocultas dos adversários vêm como `"??"`.

## Atalhos na mesa
`F` desistir · `C` passar/pagar · `R` aumentar
