# Poker Hold'em — Front-end

Front-end em **HTML, CSS e JavaScript puro** (sem build, sem dependências).

## Como rodar
O front é servido pelo back-end Java. Na pasta `Poker`, rode `br.com.poker.web.ServidorWeb`
(veja o README principal) e abra **http://localhost:8080**.

Também funciona aberto por outro servidor (ex.: Live Server do VS Code): o `api.js`
aponta automaticamente para `http://localhost:8080/api`, desde que o servidor Java esteja rodando.

## Estrutura
```
index.html        Página 1 — Lobby: lista, filtra e cria salas
mesa.html         Página 2 — Mesa de jogo (mesa.html?sala=ID)
css/base.css      Estilos compartilhados (cores, botões, cartas, modal)
css/lobby.css     Estilos do lobby
css/mesa.css      Estilos da mesa
js/api.js         Camada de comunicação com o back-end Java
js/lobby.js       Lógica da página de salas
js/mesa.js        Desenha a mesa a partir do "estado" e envia ações
```

## Modo simulado (sem servidor)
`js/api.js` ainda traz um mock completo (salas fictícias e bots em JavaScript).
Para usá-lo, mude `USAR_MOCK` para `true`. Útil para trabalhar só no visual.

## Comunicação com o back-end
| Função | Back-end |
|---|---|
| `listarSalas()` | `GET /api/salas` |
| `obterSala(id)` | `GET /api/salas/:id` |
| `criarSala(dados)` | `POST /api/salas` (inclui `bots` e `dificuldade`) |
| `conectarMesa(...)` | `EventSource /api/salas/:id/eventos?nome=APELIDO` + `POST .../acao`, `.../chat`, `.../sair` |

### Formato de uma sala
```json
{ "id": "copacabana", "nome": "Copacabana", "smallBlind": 10, "bigBlind": 20,
  "buyIn": 2000, "maxJogadores": 8, "jogadores": 6, "emJogo": true, "privada": false }
```

### Mensagens
Servidor → cliente (cada evento SSE traz um JSON):
```json
{ "tipo": "bemvindo", "cliente": "uuid-da-conexao" }
{ "tipo": "estado", "estado": { ...ver abaixo... } }
{ "tipo": "log",  "texto": "Ana: pagou 20" }
{ "tipo": "chat", "autor": "Ana", "texto": "gg", "voce": false }
{ "tipo": "erro", "texto": "A sala está cheia." }
```
Cliente → servidor (POST com JSON):
```json
POST /api/salas/:id/acao  { "cliente": "uuid", "acao": "desistir|passar|pagar|aumentar|allin", "valor": 120 }
POST /api/salas/:id/chat  { "cliente": "uuid", "texto": "boa sorte!" }
POST /api/salas/:id/sair  { "cliente": "uuid" }
```
(`valor` só em `aumentar`, e é o valor **total** apostado na rodada.)

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
