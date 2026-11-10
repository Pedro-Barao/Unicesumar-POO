/* ============ Página: Lobby (escolha de sala) ============ */
(() => {
  const $ = (s) => document.querySelector(s);
  const fmt = PokerAPI.util.formatar;
  let salas = [];

  /* ---------- Apelido ---------- */
  const inputApelido = $('#apelido');
  inputApelido.value = PokerAPI.armazenamento.ler('poker.apelido', '');
  atualizarAvatar();
  inputApelido.addEventListener('input', () => {
    PokerAPI.armazenamento.gravar('poker.apelido', inputApelido.value.trim());
    atualizarAvatar();
  });
  function atualizarAvatar() {
    $('#avatar').textContent = (inputApelido.value.trim()[0] || '?').toUpperCase();
  }

  /* ---------- Carregar salas ---------- */
  async function carregar() {
    $('#listaSalas').innerHTML = '<div class="esqueleto"></div>'.repeat(6);
    try {
      salas = await PokerAPI.listarSalas();
      render();
    } catch (e) {
      $('#listaSalas').innerHTML = '';
      toast('Erro ao carregar salas: ' + e.message);
    }
  }

  function nivel(s) {
    if (s.bigBlind <= 20) return 'baixo';
    if (s.bigBlind <= 200) return 'medio';
    return 'alto';
  }
  const CORES_NIVEL = { baixo: 'var(--verde)', medio: 'var(--ouro)', alto: 'var(--vermelho)' };

  function render() {
    const termo = $('#busca').value.trim().toLowerCase();
    const filtroNivel = $('#filtroBlinds').value;
    const ocultarCheias = $('#ocultarCheias').checked;

    const visiveis = salas.filter((s) =>
      s.nome.toLowerCase().includes(termo) &&
      (filtroNivel === 'todas' || nivel(s) === filtroNivel) &&
      (!ocultarCheias || s.jogadores < s.maxJogadores)
    );

    // Resumo
    $('#statSalas').textContent = salas.length;
    $('#statJogadores').textContent = salas.reduce((t, s) => t + s.jogadores, 0);
    $('#statLivres').textContent = salas.reduce((t, s) => t + (s.maxJogadores - s.jogadores), 0);

    $('#semSalas').classList.toggle('oculto', visiveis.length > 0);
    $('#listaSalas').innerHTML = visiveis.map(cartaoSala).join('');
  }

  function cartaoSala(s) {
    const cheia = s.jogadores >= s.maxJogadores;
    const selo = cheia
      ? '<span class="selo cheia">Cheia</span>'
      : s.emJogo ? '<span class="selo">● Em jogo</span>' : '<span class="selo aguardando">Aguardando</span>';
    const pontos = Array.from({ length: s.maxJogadores }, (_, i) =>
      `<i class="${i < s.jogadores ? 'ocupado' : ''}"></i>`).join('');
    return `
      <article class="sala ${cheia ? 'cheia' : ''}" style="--cor-nivel:${CORES_NIVEL[nivel(s)]}">
        <div class="cab">
          <h3>${s.privada ? '🔒 ' : ''}${escapar(s.nome)}</h3>
          ${selo}
        </div>
        <div class="info">
          <div><small>Blinds</small><strong>${fmt(s.smallBlind)} / ${fmt(s.bigBlind)}</strong></div>
          <div><small>Buy-in</small><strong>${fmt(s.buyIn)}</strong></div>
        </div>
        <div class="lugares">
          <div class="pontos">${pontos}</div>
          <span>${s.jogadores}/${s.maxJogadores}</span>
        </div>
        <button class="btn ${cheia ? '' : 'btn-verde'}" data-entrar="${s.id}" ${cheia ? 'disabled' : ''}>
          ${cheia ? 'Sala cheia' : 'Entrar na mesa'}
        </button>
      </article>`;
  }

  function escapar(t) {
    return String(t).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }

  /* ---------- Entrar em sala ---------- */
  function entrar(id) {
    if (!inputApelido.value.trim()) {
      inputApelido.focus();
      toast('Digite um apelido antes de entrar.');
      return;
    }
    location.href = `mesa.html?sala=${encodeURIComponent(id)}`;
  }

  $('#listaSalas').addEventListener('click', (e) => {
    const b = e.target.closest('[data-entrar]');
    if (b) entrar(b.dataset.entrar);
  });

  /* ---------- Filtros ---------- */
  $('#busca').addEventListener('input', render);
  $('#filtroBlinds').addEventListener('change', render);
  $('#ocultarCheias').addEventListener('change', render);
  $('#btnAtualizar').addEventListener('click', carregar);

  /* ---------- Modal criar sala ---------- */
  const modal = $('#modalCriar');
  const abrirModal = () => { modal.classList.add('aberto'); $('#nomeSala').focus(); };
  const fecharModal = () => modal.classList.remove('aberto');
  $('#btnCriar').addEventListener('click', abrirModal);
  modal.addEventListener('click', (e) => { if (e.target === modal || e.target.matches('[data-fechar]')) fecharModal(); });
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape') fecharModal(); });

  const atualizarBuyIn = () => {
    const bb = Number($('#blinds').value.split('/')[1]);
    $('#buyInTexto').textContent = fmt(bb * 100);
  };
  $('#blinds').addEventListener('change', atualizarBuyIn);
  atualizarBuyIn();

  $('#formCriar').addEventListener('submit', async (e) => {
    e.preventDefault();
    if (!inputApelido.value.trim()) {
      fecharModal();
      inputApelido.focus();
      return toast('Digite um apelido antes de criar a sala.');
    }
    const [sb, bb] = $('#blinds').value.split('/').map(Number);
    const botao = e.submitter;
    botao.disabled = true;
    try {
      const sala = await PokerAPI.criarSala({
        nome: $('#nomeSala').value.trim(),
        smallBlind: sb,
        bigBlind: bb,
        buyIn: bb * 100,
        maxJogadores: Number($('#maxJogadores').value),
        bots: Math.min(Number($('#qtdBots').value), Number($('#maxJogadores').value) - 1),
        dificuldade: Number($('#dificuldade').value),
        privada: $('#privada').checked,
      });
      entrar(sala.id);
    } catch (err) {
      toast(err.message);
      botao.disabled = false;
    }
  });

  /* ---------- Toast ---------- */
  let tToast;
  function toast(msg) {
    const t = $('#toast');
    t.textContent = msg;
    t.classList.add('visivel');
    clearTimeout(tToast);
    tToast = setTimeout(() => t.classList.remove('visivel'), 2600);
  }

  carregar();
})();
