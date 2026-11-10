/* ============ Página: Mesa de jogo ============
 * Esta página apenas DESENHA o estado recebido de PokerAPI.conectarMesa()
 * e envia as ações do jogador. Toda a regra do jogo fica no back-end
 * (hoje, no mock dentro de api.js).
 */
(async () => {
  const $ = (s) => document.querySelector(s);
  const fmt = PokerAPI.util.formatar;

  const NAIPES = { s: '♠', h: '♥', d: '♦', c: '♣' };
  const FASES = { aguardando: 'Aguardando', 'pre-flop': 'Pré-flop', flop: 'Flop', turn: 'Turn', river: 'River', showdown: 'Showdown' };
  const CORES = ['#c0563f', '#3f7fc0', '#8a4fc0', '#3fa37a', '#c08a3f', '#c03f7d', '#4fb3c0', '#7a8a3f'];

  let estado = null;
  let conexao = null;
  let qtdCartasMesa = 0;
  let valorEscolhido = null;

  /* ---------- Entrada na sala ---------- */
  const salaId = new URLSearchParams(location.search).get('sala');
  if (!salaId) { location.href = 'index.html'; return; }
  const apelido = PokerAPI.armazenamento.ler('poker.apelido', '') || 'Jogador';

  let sala;
  try {
    sala = await PokerAPI.obterSala(salaId);
  } catch (e) {
    alertaEVolta(e.message);
    return;
  }
  document.title = `Poker — ${sala.nome}`;
  $('#nomeSala').textContent = sala.nome;
  $('#blindsSala').textContent = `Blinds ${fmt(sala.smallBlind)}/${fmt(sala.bigBlind)}`;

  conexao = PokerAPI.conectarMesa(sala, apelido, (e) => { estado = e; render(); }, aoEvento);
  $('#btnSair').addEventListener('click', () => conexao.sair());

  /* ---------- Utilidades de desenho ---------- */
  function carta(c, extra = '') {
    if (!c) return `<span class="carta vazia ${extra}"></span>`;
    if (c === '??') return `<span class="carta verso ${extra}"></span>`;
    const valor = c[0] === 'T' ? '10' : c[0];
    const n = NAIPES[c[1]];
    const verm = c[1] === 'h' || c[1] === 'd' ? 'vermelha' : '';
    return `<span class="carta ${verm} ${extra}" aria-label="${valor}${n}">
      <span class="canto">${valor}<small>${n}</small></span><span class="centro">${n}</span></span>`;
  }

  function corDoNome(nome) {
    let h = 0;
    for (const ch of nome) h = (h * 31 + ch.charCodeAt(0)) >>> 0;
    return CORES[h % CORES.length];
  }

  function escapar(t) {
    return String(t).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }

  /** Posição (%) ao redor da mesa. Seu lugar fica sempre embaixo, no centro. */
  function posicao(assento, raioX, raioY, desvioGraus = 0) {
    const n = estado.assentos.length;
    const rel = (assento - estado.meuAssento + n) % n;
    const ang = ((90 + (rel * 360) / n + desvioGraus) * Math.PI) / 180;
    return { x: 50 + raioX * Math.cos(ang), y: 50 + raioY * Math.sin(ang) };
  }

  /* ---------- Render principal ---------- */
  function render() {
    if (!estado) return;
    $('#fase').textContent = FASES[estado.fase] || estado.fase;

    // Pote (inclui apostas da rodada atual)
    const apostasRodada = estado.assentos.reduce((t, a) => t + (a.aposta || 0), 0);
    $('#pote strong').textContent = fmt(estado.pote + apostasRodada);

    // Cartas comunitárias (anima só as novas)
    if (estado.cartasMesa.length < qtdCartasMesa) qtdCartasMesa = 0;
    $('#cartasMesa').innerHTML = Array.from({ length: 5 }, (_, i) =>
      carta(estado.cartasMesa[i], i >= qtdCartasMesa && estado.cartasMesa[i] ? 'entrar' : '')).join('');
    qtdCartasMesa = estado.cartasMesa.length;

    // Resultado da mão
    const res = $('#resultado');
    res.classList.toggle('oculto', !estado.resultado);
    res.textContent = estado.resultado || '';

    renderAssentos();
    renderBarra();
  }

  function renderAssentos() {
    let html = '';
    for (const a of estado.assentos) {
      const p = posicao(a.assento, 50, 54);
      if (a.vazio) {
        html += `<div class="assento vazio" style="left:${p.x}%;top:${p.y}%"><div class="caixa">Lugar vazio</div></div>`;
        continue;
      }
      const classes = ['assento'];
      if (a.voce) classes.push('voce');
      if (a.desistiu && estado.fase !== 'aguardando') classes.push('desistiu');
      if (a.vencedor) classes.push('vencedor');
      if (estado.vezDe === a.assento) classes.push('vez');

      let tag = '';
      if (a.maoNome && !a.desistiu) tag = `<span class="tag-acao mao">${a.maoNome}</span>`;
      else if (a.allin) tag = '<span class="tag-acao allin">ALL-IN</span>';
      else if (a.ultimaAcao) tag = `<span class="tag-acao">${escapar(a.ultimaAcao)}</span>`;

      html += `
        <div class="${classes.join(' ')}" style="left:${p.x}%;top:${p.y}%">
          <div class="cartas">${a.desistiu ? '' : a.cartas.map((c) => carta(c)).join('')}</div>
          <div class="caixa">
            <div class="av" style="--cor:${corDoNome(a.nome)}">${escapar(a.nome[0].toUpperCase())}</div>
            <div class="nome">${escapar(a.nome)}</div>
            <div class="fichas">${fmt(a.fichas)}</div>
            ${estado.vezDe === a.assento ? '<div class="timer"><i></i></div>' : ''}
          </div>
          ${tag}
        </div>`;

      if (a.aposta > 0) {
        const b = posicao(a.assento, 31, 30);
        html += `<div class="aposta" style="left:${b.x}%;top:${b.y}%"><span class="ficha" style="--c:${corDoNome(a.nome)}"></span>${fmt(a.aposta)}</div>`;
      }
    }
    if (estado.dealer >= 0) {
      const d = posicao(estado.dealer, 34, 33, -14);
      html += `<div class="dealer" style="left:${d.x}%;top:${d.y}%" title="Dealer">D</div>`;
    }
    $('#assentos').innerHTML = html;
    atualizarTimer();
  }

  /* ---------- Barra do jogador ---------- */
  function renderBarra() {
    const eu = estado.assentos[estado.meuAssento];
    $('#minhasFichas').textContent = fmt(eu.fichas);

    const temCartas = eu.cartas.length === 2 && !eu.desistiu;
    $('#minhasCartas').innerHTML = temCartas ? eu.cartas.map((c) => carta(c)).join('') : carta(null) + carta(null);
    $('#nomeMao').textContent = eu.desistiu && estado.fase !== 'aguardando'
      ? 'Você desistiu desta mão'
      : temCartas ? PokerAPI.util.nomeMao([...eu.cartas, ...estado.cartasMesa]) : 'Aguardando cartas…';

    const minhaVez = estado.vezDe === estado.meuAssento && !['showdown', 'aguardando'].includes(estado.fase);
    $('#acoes').classList.toggle('desabilitado', !minhaVez);
    $('#espera').classList.toggle('oculto', minhaVez);
    $('#espera').textContent = eu.desistiu ? 'Aguardando a próxima mão…' : 'Aguardando os outros jogadores…';
    if (!minhaVez) { valorEscolhido = null; return; }

    const paraPagar = Math.max(0, estado.apostaAtual - eu.aposta);
    const maximo = eu.aposta + eu.fichas;
    const minimo = Math.min(estado.apostaAtual + estado.minRaise, maximo);
    const podeAumentar = maximo > estado.apostaAtual;

    // Passar / Pagar
    const btnPassar = $('#btnPassar');
    if (paraPagar === 0) btnPassar.textContent = 'Passar';
    else if (paraPagar >= eu.fichas) btnPassar.textContent = `All-in ${fmt(eu.fichas)}`;
    else btnPassar.textContent = `Pagar ${fmt(paraPagar)}`;

    // Aumentar
    $('#painelAumento').classList.toggle('oculto', !podeAumentar || minimo >= maximo);
    $('#btnAumentar').classList.toggle('oculto', !podeAumentar);
    const slider = $('#sliderAumento');
    slider.min = minimo;
    slider.max = maximo;
    slider.step = estado.sala.smallBlind;
    if (valorEscolhido === null || valorEscolhido < minimo || valorEscolhido > maximo) valorEscolhido = minimo;
    definirValor(valorEscolhido);
  }

  function definirValor(v) {
    const eu = estado.assentos[estado.meuAssento];
    const maximo = eu.aposta + eu.fichas;
    const minimo = Math.min(estado.apostaAtual + estado.minRaise, maximo);
    valorEscolhido = Math.round(Math.max(minimo, Math.min(maximo, v)));
    $('#sliderAumento').value = valorEscolhido;
    $('#valorAumento').value = valorEscolhido;
    const verbo = estado.apostaAtual === 0 ? 'Apostar' : 'Aumentar p/';
    $('#btnAumentar').textContent = valorEscolhido >= maximo ? `All-in ${fmt(maximo)}` : `${verbo} ${fmt(valorEscolhido)}`;
  }

  /* ---------- Timer de ação ---------- */
  function atualizarTimer() {
    const barra = document.querySelector('.assento.vez .timer i');
    if (!barra || !estado?.prazoAcao) return;
    const resta = Math.max(0, estado.prazoAcao - Date.now());
    const frac = resta / estado.tempoAcao;
    barra.style.width = `${frac * 100}%`;
    barra.style.background = frac > 0.5 ? 'var(--verde)' : frac > 0.25 ? 'var(--ouro)' : 'var(--vermelho)';
  }
  setInterval(atualizarTimer, 100);

  /* ---------- Ações ---------- */
  const agir = (tipo, valor) => conexao && conexao.acao(tipo, valor);
  $('#btnDesistir').addEventListener('click', () => agir('desistir'));
  $('#btnPassar').addEventListener('click', () => {
    const eu = estado.assentos[estado.meuAssento];
    agir(estado.apostaAtual > eu.aposta ? 'pagar' : 'passar');
  });
  $('#btnAumentar').addEventListener('click', () => {
    const eu = estado.assentos[estado.meuAssento];
    if (valorEscolhido >= eu.aposta + eu.fichas) agir('allin');
    else agir('aumentar', valorEscolhido);
  });
  $('#sliderAumento').addEventListener('input', (e) => definirValor(Number(e.target.value)));
  $('#valorAumento').addEventListener('change', (e) => definirValor(Number(e.target.value)));
  document.querySelector('.atalhos').addEventListener('click', (e) => {
    const b = e.target.closest('[data-atalho]');
    if (!b || !estado) return;
    const eu = estado.assentos[estado.meuAssento];
    const paraPagar = Math.max(0, estado.apostaAtual - eu.aposta);
    const poteTotal = estado.pote + estado.assentos.reduce((t, a) => t + (a.aposta || 0), 0);
    const valores = {
      min: 0,
      meio: estado.apostaAtual + (poteTotal + paraPagar) / 2,
      pote: estado.apostaAtual + poteTotal + paraPagar,
      max: Infinity,
    };
    definirValor(valores[b.dataset.atalho]);
  });

  // Atalhos de teclado: F = desistir, C = passar/pagar, R = aumentar
  document.addEventListener('keydown', (e) => {
    if (e.target.matches('input, textarea')) return;
    const mapa = { f: '#btnDesistir', c: '#btnPassar', r: '#btnAumentar' };
    const b = mapa[e.key.toLowerCase()] && $(mapa[e.key.toLowerCase()]);
    if (b && !$('#acoes').classList.contains('desabilitado') && !b.classList.contains('oculto')) b.click();
  });

  /* ---------- Histórico e chat ---------- */
  function aoEvento(ev) {
    if (ev.tipo === 'erro') return alertaEVolta(ev.texto);
    if (ev.tipo === 'log') {
      const li = document.createElement('li');
      li.textContent = ev.texto;
      if (ev.texto.startsWith('—')) li.className = 'sep';
      adicionar($('#log'), li);
    } else if (ev.tipo === 'chat') {
      const li = document.createElement('li');
      if (ev.voce) li.className = 'voce';
      li.innerHTML = `<b>${escapar(ev.autor)}:</b> ${escapar(ev.texto)}`;
      adicionar($('#mensagens'), li);
      if ($('#chat').classList.contains('oculto')) $('#badgeChat').classList.remove('oculto');
    }
  }
  function adicionar(lista, li) {
    lista.appendChild(li);
    while (lista.children.length > 200) lista.firstChild.remove();
    lista.scrollTop = lista.scrollHeight;
  }

  document.querySelector('.abas').addEventListener('click', (e) => {
    const aba = e.target.closest('.aba');
    if (!aba) return;
    document.querySelectorAll('.aba').forEach((a) => a.classList.toggle('ativa', a === aba));
    const chat = aba.dataset.aba === 'chat';
    $('#log').classList.toggle('oculto', chat);
    $('#chat').classList.toggle('oculto', !chat);
    if (chat) { $('#badgeChat').classList.add('oculto'); $('#inputChat').focus(); }
  });

  $('#formChat').addEventListener('submit', (e) => {
    e.preventDefault();
    const t = $('#inputChat').value.trim();
    if (t) conexao.chat(t);
    $('#inputChat').value = '';
  });

  function alertaEVolta(msg) {
    const t = $('#toast');
    t.textContent = msg + ' Voltando ao lobby…';
    t.classList.add('visivel');
    setTimeout(() => (location.href = 'index.html'), 2000);
  }
})();
