/* =====================================================================
 * api.js — Camada de comunicação com o back-end
 * ---------------------------------------------------------------------
 * Todo o front-end conversa SOMENTE com o objeto `PokerAPI` abaixo.
 * Hoje ele usa um back-end SIMULADO (salas fictícias + bots na mesa),
 * para que as páginas funcionem sem servidor.
 *
 * Para integrar com o back-end real:
 *   1. Mude USAR_MOCK para false.
 *   2. Ajuste URL_API / URL_WS.
 *   3. Faça o servidor responder no formato descrito no README
 *      (lista de salas e objeto "estado da mesa").
 * ===================================================================== */

const PokerAPI = (() => {
  const USAR_MOCK = true;
  const URL_API = 'http://localhost:3000/api';
  const URL_WS = 'ws://localhost:3000/ws';

  const espera = (ms) => new Promise((r) => setTimeout(r, ms));

  /* ------------------------- Armazenamento local ------------------------ */
  const armazenamento = {
    ler(chave, padrao) {
      try {
        const v = localStorage.getItem(chave);
        return v ? JSON.parse(v) : padrao;
      } catch { return padrao; }
    },
    gravar(chave, valor) {
      try { localStorage.setItem(chave, JSON.stringify(valor)); } catch { /* ignora */ }
    },
  };

  /* ----------------------------- Salas (mock) --------------------------- */
  const SALAS_PADRAO = [
    { id: 'iniciantes', nome: 'Mesa dos Iniciantes', smallBlind: 5, bigBlind: 10, buyIn: 1000, maxJogadores: 8, jogadores: 3, emJogo: true, privada: false },
    { id: 'copacabana', nome: 'Copacabana', smallBlind: 10, bigBlind: 20, buyIn: 2000, maxJogadores: 8, jogadores: 6, emJogo: true, privada: false },
    { id: 'heads-up', nome: 'Heads-Up Arena', smallBlind: 25, bigBlind: 50, buyIn: 5000, maxJogadores: 2, jogadores: 1, emJogo: false, privada: false },
    { id: 'high-rollers', nome: 'High Rollers', smallBlind: 100, bigBlind: 200, buyIn: 20000, maxJogadores: 6, jogadores: 5, emJogo: true, privada: false },
    { id: 'turma-ads', nome: 'Turma de ADS', smallBlind: 5, bigBlind: 10, buyIn: 1000, maxJogadores: 8, jogadores: 8, emJogo: true, privada: false },
    { id: 'madrugada', nome: 'Madrugada', smallBlind: 50, bigBlind: 100, buyIn: 10000, maxJogadores: 6, jogadores: 2, emJogo: true, privada: true },
    { id: 'treino', nome: 'Sala de Treino', smallBlind: 1, bigBlind: 2, buyIn: 200, maxJogadores: 4, jogadores: 0, emJogo: false, privada: false },
    { id: 'final', nome: 'Final Table', smallBlind: 250, bigBlind: 500, buyIn: 50000, maxJogadores: 8, jogadores: 7, emJogo: true, privada: false },
  ];

  function salasMock() {
    return [...SALAS_PADRAO, ...armazenamento.ler('poker.salasCriadas', [])];
  }

  /* =============================== API ================================ */

  /** Lista as salas disponíveis. */
  async function listarSalas() {
    if (USAR_MOCK) {
      await espera(250);
      return structuredClone(salasMock());
    }
    const r = await fetch(`${URL_API}/salas`);
    if (!r.ok) throw new Error('Falha ao carregar salas');
    return r.json();
  }

  /** Busca os dados de uma sala específica. */
  async function obterSala(id) {
    if (USAR_MOCK) {
      await espera(100);
      const s = salasMock().find((x) => x.id === id);
      if (!s) throw new Error('Sala não encontrada');
      return structuredClone(s);
    }
    const r = await fetch(`${URL_API}/salas/${encodeURIComponent(id)}`);
    if (!r.ok) throw new Error('Sala não encontrada');
    return r.json();
  }

  /** Cria uma sala nova. dados = { nome, smallBlind, bigBlind, buyIn, maxJogadores, privada } */
  async function criarSala(dados) {
    if (USAR_MOCK) {
      await espera(300);
      const sala = {
        id: 'sala-' + Date.now().toString(36),
        jogadores: 0,
        emJogo: false,
        ...dados,
      };
      const criadas = armazenamento.ler('poker.salasCriadas', []);
      criadas.push(sala);
      armazenamento.gravar('poker.salasCriadas', criadas);
      return sala;
    }
    const r = await fetch(`${URL_API}/salas`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(dados),
    });
    if (!r.ok) throw new Error('Não foi possível criar a sala');
    return r.json();
  }

  /**
   * Conecta à mesa de uma sala.
   *   onEstado(estado)  -> chamado sempre que o estado da mesa mudar
   *   onEvento(evento)  -> { tipo: 'log' | 'chat', texto, autor? }
   * Retorna { acao(tipo, valor), chat(texto), sair() }
   *   tipo de ação: 'desistir' | 'passar' | 'pagar' | 'aumentar' | 'allin'
   *   valor (só em 'aumentar'): valor TOTAL da aposta na rodada
   */
  function conectarMesa(sala, nomeJogador, onEstado, onEvento) {
    if (USAR_MOCK) {
      const mesa = new MesaSimulada(sala, nomeJogador, onEstado, onEvento);
      mesa.iniciar();
      return {
        acao: (tipo, valor) => mesa.acaoJogador(tipo, valor),
        chat: (texto) => mesa.chat(texto),
        sair: () => mesa.parar(),
      };
    }

    // ---- Exemplo de integração via WebSocket ----
    const ws = new WebSocket(`${URL_WS}?sala=${encodeURIComponent(sala.id)}&nome=${encodeURIComponent(nomeJogador)}`);
    ws.onmessage = (msg) => {
      const dados = JSON.parse(msg.data);
      if (dados.tipo === 'estado') onEstado(dados.estado);
      else onEvento(dados);
    };
    ws.onclose = () => onEvento({ tipo: 'log', texto: 'Conexão encerrada.' });
    const enviar = (obj) => ws.readyState === 1 && ws.send(JSON.stringify(obj));
    return {
      acao: (tipo, valor) => enviar({ tipo: 'acao', acao: tipo, valor }),
      chat: (texto) => enviar({ tipo: 'chat', texto }),
      sair: () => ws.close(),
    };
  }

  /* ======================= Avaliador de mãos ========================== */
  const VALORES = '23456789TJQKA';
  const NOMES_MAOS = ['Carta alta', 'Par', 'Dois pares', 'Trinca', 'Sequência', 'Flush', 'Full house', 'Quadra', 'Straight flush'];

  function avaliar5(cartas) {
    const v = cartas.map((c) => VALORES.indexOf(c[0])).sort((a, b) => b - a);
    const flush = cartas.every((c) => c[1] === cartas[0][1]);
    let seq = -1;
    if (new Set(v).size === 5) {
      if (v[0] - v[4] === 4) seq = v[0];
      else if (v.join() === '12,3,2,1,0') seq = 3; // A-2-3-4-5
    }
    const cont = {};
    v.forEach((x) => (cont[x] = (cont[x] || 0) + 1));
    const g = Object.entries(cont).map(([k, n]) => [n, +k]).sort((a, b) => b[0] - a[0] || b[1] - a[1]);
    const ord = g.map((x) => x[1]);
    if (seq >= 0 && flush) return [8, seq];
    if (g[0][0] === 4) return [7, ...ord];
    if (g[0][0] === 3 && g[1][0] === 2) return [6, ...ord];
    if (flush) return [5, ...v];
    if (seq >= 0) return [4, seq];
    if (g[0][0] === 3) return [3, ...ord];
    if (g[0][0] === 2 && g[1][0] === 2) return [2, ...ord];
    if (g[0][0] === 2) return [1, ...ord];
    return [0, ...v];
  }

  function comparar(a, b) {
    for (let i = 0; i < Math.max(a.length, b.length); i++) {
      const d = (a[i] ?? -1) - (b[i] ?? -1);
      if (d) return d;
    }
    return 0;
  }

  /** Melhor mão possível com 5 a 7 cartas. */
  function melhorMao(cartas) {
    if (cartas.length < 5) return null;
    let melhor = null;
    const n = cartas.length;
    for (let a = 0; a < n; a++) for (let b = a + 1; b < n; b++) for (let c = b + 1; c < n; c++)
      for (let d = c + 1; d < n; d++) for (let e = d + 1; e < n; e++) {
        const s = avaliar5([cartas[a], cartas[b], cartas[c], cartas[d], cartas[e]]);
        if (!melhor || comparar(s, melhor) > 0) melhor = s;
      }
    return melhor;
  }

  /** Nome da mão atual (ex.: "Dois pares"). Funciona com 2 cartas também. */
  function nomeMao(cartas) {
    if (cartas.length === 2) return cartas[0][0] === cartas[1][0] ? 'Par' : 'Carta alta';
    const m = melhorMao(cartas);
    return m ? NOMES_MAOS[m[0]] : '';
  }

  /* ====================== Mesa simulada (mock) ======================== */
  const NOMES_BOTS = ['Ana', 'Bruno', 'Carla', 'Diego', 'Eduarda', 'Felipe', 'Gabi', 'Heitor', 'Isa', 'João', 'Lara', 'Marcos'];
  const TEMPO_ACAO = 20000;

  class MesaSimulada {
    constructor(sala, nome, onEstado, onEvento) {
      this.sala = sala;
      this.onEstado = onEstado;
      this.onEvento = onEvento;
      this.n = sala.maxJogadores;
      this.assentos = Array(this.n).fill(null);
      this.meu = 0;
      this.assentos[0] = this.criarJogador(nome, true);
      const qtdBots = Math.max(1, Math.min(sala.jogadores || 3, this.n - 1));
      const nomes = [...NOMES_BOTS].sort(() => Math.random() - 0.5);
      const livres = [...Array(this.n - 1).keys()].map((i) => i + 1).sort(() => Math.random() - 0.5);
      for (let i = 0; i < qtdBots; i++) this.assentos[livres[i]] = this.criarJogador(nomes[i], false);
      this.dealer = -1;
      this.timers = new Set();
      this.fase = 'aguardando';
      this.mesa = [];
      this.pote = 0;
    }

    criarJogador(nome, voce) {
      return { nome, voce, fichas: this.sala.buyIn, aposta: 0, cartas: [], desistiu: true, allin: false, agiu: false, ultimaAcao: null, vencedor: false, maoNome: null };
    }

    agendar(fn, ms) {
      const t = setTimeout(() => { this.timers.delete(t); fn(); }, ms);
      this.timers.add(t);
      return t;
    }
    parar() { this.timers.forEach(clearTimeout); this.timers.clear(); this.parado = true; }
    log(texto) { this.onEvento({ tipo: 'log', texto }); }

    iniciar() {
      this.log(`Você entrou na sala ${this.sala.nome}.`);
      this.emitir();
      this.agendar(() => this.novaMao(), 1200);
    }

    ocupados() { return this.assentos.map((p, i) => (p ? i : -1)).filter((i) => i >= 0); }
    proximo(i, filtro = (p) => p) {
      for (let k = 1; k <= this.n; k++) {
        const j = (i + k) % this.n;
        if (this.assentos[j] && filtro(this.assentos[j])) return j;
      }
      return -1;
    }
    podeAgir(p) { return !p.desistiu && !p.allin; }

    novaMao() {
      if (this.parado) return;
      // Reabastece quem quebrou (é só uma simulação)
      this.assentos.forEach((p) => {
        if (p && p.fichas <= 0) {
          p.fichas = this.sala.buyIn;
          this.log(`${p.voce ? 'Você recomprou' : p.nome + ' recomprou'} ${fmt(this.sala.buyIn)} fichas.`);
        }
      });
      this.fase = 'pre-flop';
      this.mesa = [];
      this.pote = 0;
      this.resultado = null;
      this.baralho = novoBaralho();
      this.dealer = this.proximo(this.dealer < 0 ? this.n - 1 : this.dealer);
      for (const i of this.ocupados()) {
        Object.assign(this.assentos[i], { aposta: 0, cartas: [this.baralho.pop(), this.baralho.pop()], desistiu: false, allin: false, agiu: false, ultimaAcao: null, vencedor: false, maoNome: null });
      }
      const qtd = this.ocupados().length;
      const sb = qtd === 2 ? this.dealer : this.proximo(this.dealer);
      const bb = this.proximo(sb);
      this.apostar(sb, this.sala.smallBlind);
      this.apostar(bb, this.sala.bigBlind);
      this.assentos[sb].ultimaAcao = 'Small blind';
      this.assentos[bb].ultimaAcao = 'Big blind';
      this.apostaAtual = this.sala.bigBlind;
      this.minRaise = this.sala.bigBlind;
      this.log('— Nova mão —');
      this.passarVez(this.proximo(bb, (p) => this.podeAgir(p)));
    }

    apostar(i, valor) {
      const p = this.assentos[i];
      const v = Math.max(0, Math.min(valor, p.fichas));
      p.fichas -= v;
      p.aposta += v;
      if (p.fichas === 0) p.allin = true;
    }

    passarVez(i) {
      this.vezDe = i;
      this.prazo = Date.now() + TEMPO_ACAO;
      this.emitir();
      const p = this.assentos[i];
      if (!p) return;
      clearTimeout(this.timerVez);
      if (p.voce) {
        this.timerVez = this.agendar(() => {
          if (this.vezDe === i && this.fase !== 'showdown') {
            this.log('Tempo esgotado!');
            this.executar(i, p.aposta === this.apostaAtual ? 'passar' : 'desistir');
          }
        }, TEMPO_ACAO);
      } else {
        this.timerVez = this.agendar(() => this.jogarBot(i), 700 + Math.random() * 1300);
      }
    }

    acaoJogador(tipo, valor) {
      if (this.vezDe !== this.meu || this.fase === 'showdown' || this.fase === 'aguardando') return;
      this.executar(this.meu, tipo, valor);
    }

    executar(i, tipo, valor) {
      const p = this.assentos[i];
      const paraPagar = this.apostaAtual - p.aposta;
      const quem = p.voce ? 'Você' : p.nome;
      if (tipo === 'passar' && paraPagar > 0) tipo = 'pagar';

      if (tipo === 'desistir') {
        p.desistiu = true;
        p.ultimaAcao = 'Desistiu';
      } else if (tipo === 'passar') {
        p.ultimaAcao = 'Passou';
      } else if (tipo === 'pagar') {
        this.apostar(i, paraPagar);
        p.ultimaAcao = p.allin ? 'All-in' : `Pagou ${fmt(p.aposta)}`;
      } else if (tipo === 'aumentar' || tipo === 'allin') {
        const maximo = p.aposta + p.fichas;
        let total = tipo === 'allin' ? maximo : Math.min(Number(valor) || 0, maximo);
        if (total < this.apostaAtual + this.minRaise && total < maximo) total = this.apostaAtual + this.minRaise;
        total = Math.min(total, maximo);
        if (total > this.apostaAtual) {
          this.minRaise = Math.max(this.minRaise, total - this.apostaAtual);
          this.apostaAtual = total;
          this.assentos.forEach((o) => o && o !== p && (o.agiu = false));
        }
        this.apostar(i, total - p.aposta);
        p.ultimaAcao = p.allin ? 'All-in' : `Aumentou p/ ${fmt(p.aposta)}`;
      }
      p.agiu = true;
      this.log(`${quem}: ${p.ultimaAcao.toLowerCase()}`);
      this.avancar(i);
    }

    avancar(i) {
      const vivos = this.ocupados().filter((j) => !this.assentos[j].desistiu);
      if (vivos.length === 1) return this.finalizar([vivos[0]], false);
      const agem = vivos.map((j) => this.assentos[j]).filter((p) => !p.allin);
      const completa = agem.every((p) => (p.agiu || agem.length === 1) && p.aposta >= this.apostaAtual);
      if (completa) return this.proximaFase();
      this.passarVez(this.proximo(i, (p) => this.podeAgir(p)));
    }

    recolherApostas() {
      this.assentos.forEach((p) => {
        if (!p) return;
        this.pote += p.aposta;
        p.aposta = 0;
        p.agiu = false;
      });
      this.apostaAtual = 0;
      this.minRaise = this.sala.bigBlind;
    }

    proximaFase() {
      this.recolherApostas();
      this.assentos.forEach((p) => p && !p.desistiu && !p.allin && (p.ultimaAcao = null));
      const ordem = { 'pre-flop': 'flop', flop: 'turn', turn: 'river', river: 'showdown' };
      this.fase = ordem[this.fase];
      if (this.fase === 'flop') this.mesa.push(this.baralho.pop(), this.baralho.pop(), this.baralho.pop());
      else if (this.fase === 'turn' || this.fase === 'river') this.mesa.push(this.baralho.pop());
      if (this.fase === 'showdown') return this.showdown();
      this.log(`${this.fase.toUpperCase()}: ${this.mesa.join(' ')}`);

      const podem = this.ocupados().filter((j) => this.podeAgir(this.assentos[j]));
      if (podem.length <= 1) {
        // Ninguém mais pode apostar: revela as cartas restantes automaticamente
        this.vezDe = -1;
        this.emitir();
        return this.agendar(() => this.proximaFase(), 1400);
      }
      this.passarVez(this.proximo(this.dealer, (p) => this.podeAgir(p)));
    }

    showdown() {
      const vivos = this.ocupados().filter((j) => !this.assentos[j].desistiu);
      let melhor = null;
      let vencedores = [];
      for (const j of vivos) {
        const p = this.assentos[j];
        const m = melhorMao([...p.cartas, ...this.mesa]);
        p.maoNome = NOMES_MAOS[m[0]];
        const c = melhor ? comparar(m, melhor) : 1;
        if (c > 0) { melhor = m; vencedores = [j]; } else if (c === 0) vencedores.push(j);
      }
      this.finalizar(vencedores, true);
    }

    finalizar(vencedores, revelar) {
      this.recolherApostas();
      this.fase = 'showdown';
      this.vezDe = -1;
      this.revelar = revelar;
      const parte = Math.floor(this.pote / vencedores.length);
      const nomes = vencedores.map((j) => {
        const p = this.assentos[j];
        p.fichas += parte;
        p.vencedor = true;
        return p.voce ? 'Você' : p.nome;
      });
      const mao = revelar ? ` com ${this.assentos[vencedores[0]].maoNome}` : '';
      this.resultado = `${nomes.join(' e ')} ${nomes.length > 1 || nomes[0] === 'Você' ? 'ganharam' : 'ganhou'} ${fmt(this.pote)}${mao}`;
      if (nomes.length === 1 && nomes[0] === 'Você') this.resultado = `Você ganhou ${fmt(this.pote)}${mao}!`;
      this.log(this.resultado);
      this.emitir();
      this.agendar(() => { this.revelar = false; this.novaMao(); }, 5000);
    }

    jogarBot(i) {
      if (this.vezDe !== i || this.parado) return;
      const p = this.assentos[i];
      const paraPagar = this.apostaAtual - p.aposta;
      // "Força" aproximada: 0 a 1
      const cartas = [...p.cartas, ...this.mesa];
      let forca;
      if (this.mesa.length === 0) {
        const [a, b] = p.cartas.map((c) => VALORES.indexOf(c[0]));
        forca = (a + b) / 24 + (a === b ? 0.35 : 0) + (p.cartas[0][1] === p.cartas[1][1] ? 0.05 : 0);
      } else {
        forca = melhorMao(cartas)[0] / 4 + Math.random() * 0.2;
      }
      const r = Math.random();
      if (paraPagar === 0) {
        if (forca > 0.7 && r < 0.5) return this.executar(i, 'aumentar', this.apostaAtual + this.minRaise * (1 + Math.floor(r * 4)));
        return this.executar(i, 'passar');
      }
      const caro = paraPagar / (p.fichas + p.aposta);
      if (forca < 0.35 && r < 0.55 + caro) return this.executar(i, 'desistir');
      if (forca > 0.85 && r < 0.35) return this.executar(i, 'aumentar', this.apostaAtual + this.minRaise * 2);
      if (caro > 0.6 && forca < 0.6) return this.executar(i, 'desistir');
      return this.executar(i, 'pagar');
    }

    chat(texto) {
      this.onEvento({ tipo: 'chat', autor: this.assentos[this.meu].nome, texto, voce: true });
      if (Math.random() < 0.5) {
        const bots = this.ocupados().filter((j) => j !== this.meu);
        const autor = this.assentos[bots[Math.floor(Math.random() * bots.length)]].nome;
        const respostas = ['haha', 'boa sorte!', 'vamos ver...', 'gg', 'tô de olho 👀', 'essa mão é minha'];
        this.agendar(() => this.onEvento({ tipo: 'chat', autor, texto: respostas[Math.floor(Math.random() * respostas.length)] }), 1200);
      }
    }

    /** Monta o estado público (o que o servidor real enviaria ao cliente). */
    emitir() {
      if (this.parado) return;
      const verCartas = (p) => p.voce || (this.revelar && !p.desistiu);
      this.onEstado({
        sala: { id: this.sala.id, nome: this.sala.nome, smallBlind: this.sala.smallBlind, bigBlind: this.sala.bigBlind },
        fase: this.fase,
        pote: this.pote,
        cartasMesa: [...this.mesa],
        apostaAtual: this.apostaAtual || 0,
        minRaise: this.minRaise || this.sala.bigBlind,
        dealer: this.dealer,
        vezDe: this.vezDe ?? -1,
        prazoAcao: this.prazo,
        tempoAcao: TEMPO_ACAO,
        meuAssento: this.meu,
        resultado: this.resultado,
        assentos: this.assentos.map((p, i) => p ? {
          assento: i,
          nome: p.nome,
          fichas: p.fichas,
          aposta: p.aposta,
          cartas: p.cartas.length ? (verCartas(p) ? [...p.cartas] : ['??', '??']) : [],
          desistiu: p.desistiu,
          allin: p.allin,
          ultimaAcao: p.ultimaAcao,
          voce: p.voce,
          vencedor: p.vencedor,
          maoNome: this.revelar ? p.maoNome : null,
        } : { assento: i, vazio: true }),
      });
    }
  }

  function novoBaralho() {
    const b = [];
    for (const v of VALORES) for (const n of 'shdc') b.push(v + n);
    for (let i = b.length - 1; i > 0; i--) {
      const j = Math.floor(Math.random() * (i + 1));
      [b[i], b[j]] = [b[j], b[i]];
    }
    return b;
  }

  function fmt(n) { return Number(n).toLocaleString('pt-BR'); }

  return {
    listarSalas,
    obterSala,
    criarSala,
    conectarMesa,
    armazenamento,
    util: { nomeMao, formatar: fmt },
  };
})();
