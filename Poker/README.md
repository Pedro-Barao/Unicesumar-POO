# Poker Texas Hold'em (Java + Web)

O projeto tem duas formas de jogar, que usam as mesmas classes do pacote `model`:

| Modo | Classe principal | Como joga |
|---|---|---|
| **Web** (novo) | `br.com.poker.web.ServidorWeb` | Navegador: lobby de salas + mesa com bots e vários jogadores |
| Console | `br.com.poker.main.Main` | Terminal, com Scanner |

## Rodar a versão web

**No VS Code:** abra a pasta `Poker`, abra `src/br/com/poker/web/ServidorWeb.java` e clique em **Run**.
Depois acesse **http://localhost:8080**.

**No terminal** (dentro da pasta `Poker`, Java 17 ou mais novo):

```bash
javac -d bin $(find src -name "*.java")
java -cp bin br.com.poker.web.ServidorWeb
```

No Windows (PowerShell):

```powershell
javac -d bin (Get-ChildItem -Recurse src -Filter *.java).FullName
java -cp bin br.com.poker.web.ServidorWeb
```

Argumentos opcionais: `java -cp bin br.com.poker.web.ServidorWeb 9000 caminho/do/poker-frontend`.
Não precisa de Maven, Spring nem nenhuma biblioteca: só o JDK.

Para jogar com amigos na mesma rede, eles acessam `http://SEU_IP:8080` e entram na mesma sala.

## Como back e front se conectam

```
poker-frontend (HTML/CSS/JS)                 src/br/com/poker
┌──────────────────────────┐                 ┌───────────────────────────────┐
│ index.html  + lobby.js   │ ── GET/POST ──▶ │ web/ServidorWeb  (HTTP do JDK)│
│ mesa.html   + mesa.js    │                 │   └─ web/MesaOnline (1 por sala)
│        js/api.js         │ ◀── SSE ─────── │        usa model/Jogador, Bot,│
└──────────────────────────┘   (estado da    │        Cartas, AcoesJogo,     │
                                mesa ao vivo)│        AvaliadorMaos          │
                                             └───────────────────────────────┘
```

- `ServidorWeb` entrega os arquivos do front e responde a API (`/api/salas...`).
- `MesaOnline` tem a mesma regra da classe `Jogo`, mas sem `Scanner` e `Thread.sleep`.
  A mesa avança a cada ação recebida do navegador ou do `Bot`, e envia o novo estado
  a todos os conectados. As decisões dos bots continuam vindo de `Bot.decisao(...)`.
- O estado chega ao navegador por **Server-Sent Events** (uma conexão que o servidor mantém
  aberta). As jogadas vão por `POST`. Cada jogador recebe só as próprias cartas.
- `AvaliadorMaos` (novo, em `model`) compara as mãos no showdown e divide o pote,
  inclusive os side pots quando alguém está all-in.

### API

| Método | Rota | Corpo / retorno |
|---|---|---|
| GET | `/api/salas` | lista de salas |
| GET | `/api/salas/{id}` | uma sala |
| POST | `/api/salas` | `{ nome, smallBlind, bigBlind, buyIn, maxJogadores, bots, dificuldade, privada }` |
| GET | `/api/salas/{id}/eventos?nome=Apelido` | entra na mesa; stream SSE com `bemvindo`, `estado`, `log`, `chat`, `erro` |
| POST | `/api/salas/{id}/acao` | `{ cliente, acao: "desistir/passar/pagar/aumentar/allin", valor }` |
| POST | `/api/salas/{id}/chat` | `{ cliente, texto }` |
| POST | `/api/salas/{id}/sair` | `{ cliente }` |

O formato do objeto `estado` está em `poker-frontend/README.md`.

## Estrutura

```
src/br/com/poker/
  main/Main.java            versão console
  model/                    regras: Jogador, Bot, Cartas, Jogo, GerenciarJogo, AvaliadorMaos
  enums/, exception/
  web/ServidorWeb.java      servidor HTTP + API + arquivos estáticos
  web/MesaOnline.java       mesa jogada pela web (uma instância por sala)
  web/Cliente.java          um navegador conectado
  web/Json.java             leitura/escrita de JSON sem biblioteca
poker-frontend/             front-end (HTML, CSS, JS puro)
```

## Observações

- Quem fica sem fichas recompra automaticamente o buy-in da sala, para a mesa não parar.
- A mesa só distribui cartas quando há pelo menos um humano conectado e dois jogadores sentados.
- Para mexer só no visual sem o servidor, mude `USAR_MOCK` para `true` em `poker-frontend/js/api.js`.
