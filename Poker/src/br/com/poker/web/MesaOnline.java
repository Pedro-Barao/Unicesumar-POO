package br.com.poker.web;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TreeSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import br.com.poker.enums.TipoJogadas;
import br.com.poker.model.AcoesJogo;
import br.com.poker.model.AvaliadorMaos;
import br.com.poker.model.Bot;
import br.com.poker.model.Cartas;
import br.com.poker.model.Jogador;

/**
 * Uma mesa de Texas Hold'em jogada pela web.
 *
 * É a mesma regra da classe Jogo (console), mas sem Scanner nem Thread.sleep:
 * a mesa avança quando chega uma ação (do navegador ou de um Bot) ou quando
 * um temporizador dispara. Cada mudança é enviada a todos os navegadores conectados.
 *
 * Reaproveita do projeto: Jogador, Bot (decisões e dificuldade), Cartas,
 * AcoesJogo, TipoJogadas e o novo AvaliadorMaos.
 */
public class MesaOnline {

    private static final ScheduledExecutorService AGENDADOR = Executors.newScheduledThreadPool(2, r -> {
        Thread t = new Thread(r, "mesa-agendador");
        t.setDaemon(true);
        return t;
    });

    static final int TEMPO_ACAO = 25_000;
    private static final String[] NAIPES = { " Ouros", " Copas", " Paus", " Espadas" };
    private static final String[] VALORES = { "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A" };
    private static final NumberFormat FORMATO = NumberFormat.getIntegerInstance(Locale.forLanguageTag("pt-BR"));

    /** Um lugar ocupado na mesa: o Jogador do projeto + dados que só a mesa web precisa. */
    private static final class Assento {

        final Jogador jogador;
        final Cliente cliente; // null para bots
        boolean conectado = true;
        boolean sairAoFim;
        boolean allin;
        boolean agiu;
        boolean vencedor;
        int contribuicao; // total colocado no pote nesta mão (para side pots)
        String ultimaAcao;
        String maoNome;

        Assento(Jogador jogador, Cliente cliente)
        {

            this.jogador = jogador;
            this.cliente = cliente;

        }

        boolean bot() { return jogador instanceof Bot; }
        boolean desistiu() { return !jogador.getMaoAtual(); }
        int aposta() { return jogador.getApostaNaRodada(); }
        int fichas() { return jogador.getFichas(); }
        String nome() { return jogador instanceof Bot b ? b.getNome() : jogador.getNomeJogador(); }

    }

    final String id;
    final String nome;
    final int smallBlind;
    final int bigBlind;
    final int buyIn;
    final int maxJogadores;
    final boolean privada;

    private final Assento[] assentos;
    private final List<String> baralho = new ArrayList<>();
    private final List<String> mesa = new ArrayList<>();
    private final Random random = new Random();

    private String fase = "aguardando";
    private int pote;
    private int apostaAtual;
    private int minRaise;
    private int dealer = -1;
    private int vezDe = -1;
    private long prazo;
    private boolean revelar;
    private Map<Integer, Integer> ganhos = new LinkedHashMap<>();
    private String maoVencedora;

    private ScheduledFuture<?> tarefa;
    private int token;

    public MesaOnline(String id, String nome, int smallBlind, int bigBlind, int buyIn, int maxJogadores,
                      boolean privada, int bots, int dificuldade)
    {

        this.id = id;
        this.nome = nome;
        this.smallBlind = smallBlind;
        this.bigBlind = bigBlind;
        this.buyIn = buyIn;
        this.maxJogadores = maxJogadores;
        this.privada = privada;
        this.assentos = new Assento[maxJogadores];

        // Bots em lugares aleatórios; o lugar 0 fica livre para o primeiro humano
        List<Integer> livres = new ArrayList<>();

        for (int i = 1; i < maxJogadores; i++) livres.add(i);

        Collections.shuffle(livres, random);

        for (int i = 0; i < Math.min(bots, maxJogadores - 1); i++)
        {

            Bot bot = criarBot(dificuldade);
            bot.setFichas(buyIn);
            bot.setMaoAtual(false);
            assentos[livres.get(i)] = new Assento(bot, null);

        }

    }

    private Bot criarBot(int dificuldade)
    {

        Bot bot = new Bot(dificuldade);

        for (int tentativa = 0; tentativa < 30 && nomeEmUso(bot.getNome()); tentativa++)
        {

            bot = new Bot(dificuldade);

        }

        return bot;

    }

    private boolean nomeEmUso(String n)
    {

        for (Assento a : assentos)
        {

            if (a != null && a.nome().equalsIgnoreCase(n)) return true;

        }

        return false;

    }

    /* ============================ Conexões ============================= */

    public synchronized Cliente entrar(String apelido)
    {

        String base = (apelido == null || apelido.isBlank()) ? "Jogador" : apelido.trim();

        if (base.length() > 16) base = base.substring(0, 16);

        String nomeFinal = base;

        for (int n = 2; nomeEmUso(nomeFinal); n++) nomeFinal = base + " " + n;

        int lugar = -1;

        for (int i = 0; i < maxJogadores; i++)
        {

            if (assentos[i] == null) { lugar = i; break; }

        }

        if (lugar < 0)
        {

            throw new IllegalStateException("A sala está cheia.");

        }

        Cliente cliente = new Cliente(this, lugar);
        Jogador jogador = new Jogador(nomeFinal);
        jogador.setFichas(buyIn);
        jogador.setMaoAtual(false); // entra na próxima mão
        assentos[lugar] = new Assento(jogador, cliente);

        cliente.enviar(Map.of("tipo", "bemvindo", "cliente", cliente.id));
        cliente.enviar(Map.of("tipo", "log", "texto", "Você entrou na sala " + nome + "."));
        log(nomeFinal + " sentou na mesa.");
        emitir();
        tentarComecar();

        return cliente;

    }

    public synchronized void sair(Cliente cliente)
    {

        if (!cliente.ativo) return;

        cliente.ativo = false;
        Assento a = assentos[cliente.assento];

        if (a == null || a.cliente != cliente) return;

        a.conectado = false;
        log(a.nome() + " saiu da mesa.");

        boolean emJogo = !fase.equals("aguardando") && !fase.equals("showdown");

        if (!emJogo || a.desistiu())
        {

            assentos[cliente.assento] = null;

        }

        else
        {

            a.sairAoFim = true;

            if (vezDe == cliente.assento)
            {

                executar(cliente.assento, "desistir", 0);
                return;

            }

        }

        emitir();

    }

    public synchronized void acao(Cliente cliente, String tipo, int valor)
    {

        boolean apostando = fase.equals("pre-flop") || fase.equals("flop") || fase.equals("turn") || fase.equals("river");

        if (!cliente.ativo || vezDe != cliente.assento || !apostando) return;

        executar(cliente.assento, tipo, valor);

    }

    public synchronized void chat(Cliente cliente, String texto)
    {

        Assento a = assentos[cliente.assento];

        if (a == null || texto == null || texto.isBlank()) return;

        String t = texto.trim();

        if (t.length() > 200) t = t.substring(0, 200);

        for (Assento o : assentos)
        {

            if (o != null && o.cliente != null && o.conectado)
            {

                Map<String, Object> m = new LinkedHashMap<>();
                m.put("tipo", "chat");
                m.put("autor", a.nome());
                m.put("texto", t);
                m.put("voce", o.cliente == cliente);
                o.cliente.enviar(m);

            }

        }

    }

    /* ======================= Fluxo de uma mão ========================= */

    private int humanosConectados()
    {

        int n = 0;

        for (Assento a : assentos) if (a != null && !a.bot() && a.conectado) n++;

        return n;

    }

    private int sentados()
    {

        int n = 0;

        for (Assento a : assentos) if (a != null && !a.sairAoFim) n++;

        return n;

    }

    private boolean podeComecar()
    {

        return humanosConectados() >= 1 && sentados() >= 2;

    }

    private void tentarComecar()
    {

        if (fase.equals("aguardando") && podeComecar())
        {

            agendar(this::novaMao, 1500);

        }

    }

    private void novaMao()
    {

        for (int i = 0; i < maxJogadores; i++)
        {

            if (assentos[i] != null && assentos[i].sairAoFim) assentos[i] = null;

        }

        ganhos = new LinkedHashMap<>();
        maoVencedora = null;
        revelar = false;
        mesa.clear();
        pote = 0;
        apostaAtual = 0;
        vezDe = -1;

        if (!podeComecar())
        {

            fase = "aguardando";

            for (Assento a : assentos)
            {

                if (a == null) continue;

                a.jogador.getCartas().clear();
                a.jogador.setMaoAtual(false);
                a.jogador.setApostaNaRodada(0);
                a.allin = false;
                a.vencedor = false;
                a.ultimaAcao = null;
                a.maoNome = null;

            }

            emitir();
            return;

        }

        // Recompra automática para quem quebrou (mantém a mesa viva)
        for (Assento a : assentos)
        {

            if (a != null && a.fichas() <= 0)
            {

                a.jogador.setFichas(buyIn);
                log(a.nome() + " recomprou " + fmt(buyIn) + " fichas.");

            }

        }

        // Baralho no mesmo formato do GerenciarJogo ("10 Copas")
        baralho.clear();

        for (String n : NAIPES) for (String v : VALORES) baralho.add(v + n);

        Collections.shuffle(baralho, random);

        fase = "pre-flop";
        dealer = proximo(dealer < 0 ? maxJogadores - 1 : dealer, false);

        for (Assento a : assentos)
        {

            if (a == null) continue;

            a.jogador.getCartas().clear();
            a.jogador.getCartas().add(puxar());
            a.jogador.getCartas().add(puxar());
            a.jogador.setMaoAtual(true);
            a.jogador.setApostaNaRodada(0);
            a.allin = false;
            a.agiu = false;
            a.vencedor = false;
            a.ultimaAcao = null;
            a.maoNome = null;
            a.contribuicao = 0;

        }

        int qtd = sentados();
        int sb = (qtd == 2) ? dealer : proximo(dealer, false);
        int bb = proximo(sb, false);

        apostar(sb, smallBlind);
        apostar(bb, bigBlind);
        assentos[sb].ultimaAcao = "Small blind";
        assentos[bb].ultimaAcao = "Big blind";
        apostaAtual = bigBlind;
        minRaise = bigBlind;

        log("— Nova mão —");

        int primeiro = proximo(bb, true);

        if (primeiro < 0) proximaFase();
        else passarVez(primeiro);

    }

    private String puxar()
    {

        return baralho.remove(baralho.size() - 1);

    }

    /** Próximo assento ocupado depois de i (opcionalmente só quem ainda pode agir). */
    private int proximo(int i, boolean soQuemPodeAgir)
    {

        for (int k = 1; k <= maxJogadores; k++)
        {

            int j = (i + k) % maxJogadores;
            Assento a = assentos[j];

            if (a == null) continue;
            if (soQuemPodeAgir && (a.desistiu() || a.allin)) continue;

            return j;

        }

        return -1;

    }

    private void apostar(int i, int valor)
    {

        Assento a = assentos[i];
        int v = Math.max(0, Math.min(valor, a.fichas()));

        a.jogador.setFichas(a.fichas() - v);
        a.jogador.setApostaNaRodada(a.aposta() + v);
        a.contribuicao += v;

        if (a.fichas() == 0) a.allin = true;

    }

    private void passarVez(int i)
    {

        vezDe = i;
        prazo = System.currentTimeMillis() + TEMPO_ACAO;
        emitir();

        Assento a = assentos[i];

        if (a.bot())
        {

            agendar(() -> jogarBot(i), 900 + random.nextInt(1300));

        }

        else if (!a.conectado)
        {

            agendar(() -> executar(i, "desistir", 0), 400);

        }

        else
        {

            agendar(() -> {
                log("Tempo esgotado para " + a.nome() + ".");
                executar(i, a.aposta() >= apostaAtual ? "passar" : "desistir", 0);
            }, TEMPO_ACAO);

        }

    }

    private void executar(int i, String tipo, int valor)
    {

        Assento a = assentos[i];
        int paraPagar = apostaAtual - a.aposta();

        if (tipo.equals("passar") && paraPagar > 0) tipo = "pagar";

        switch (tipo)
        {

            case "desistir" -> {
                a.jogador.setMaoAtual(false);
                a.ultimaAcao = "Desistiu";
            }

            case "passar" -> a.ultimaAcao = "Passou";

            case "pagar" -> {
                apostar(i, paraPagar);
                a.ultimaAcao = a.allin ? "All-in" : "Pagou " + fmt(a.aposta());
            }

            case "aumentar", "allin" -> {
                boolean primeiraAposta = apostaAtual == 0;
                int maximo = a.aposta() + a.fichas();
                int total = tipo.equals("allin") ? maximo : Math.min(valor, maximo);

                if (total < apostaAtual + minRaise && total < maximo) total = apostaAtual + minRaise;

                total = Math.min(total, maximo);

                if (total > apostaAtual)
                {

                    minRaise = Math.max(minRaise, total - apostaAtual);
                    apostaAtual = total;

                    for (Assento o : assentos) if (o != null && o != a) o.agiu = false;

                }

                apostar(i, total - a.aposta());
                a.ultimaAcao = a.allin ? "All-in" : (primeiraAposta ? "Apostou " : "Aumentou p/ ") + fmt(a.aposta());
            }

            default -> { return; }

        }

        a.agiu = true;
        log(a.nome() + ": " + a.ultimaAcao.toLowerCase());
        avancar(i);

    }

    private void avancar(int i)
    {

        List<Assento> vivos = new ArrayList<>();
        int ultimoVivo = -1;

        for (int j = 0; j < maxJogadores; j++)
        {

            if (assentos[j] != null && !assentos[j].desistiu())
            {

                vivos.add(assentos[j]);
                ultimoVivo = j;

            }

        }

        if (vivos.size() == 1)
        {

            recolherApostas();
            ganhos.put(ultimoVivo, pote);
            finalizar(false);
            return;

        }

        List<Assento> agem = vivos.stream().filter(a -> !a.allin).toList();
        boolean completa = agem.stream().allMatch(a -> (a.agiu || agem.size() == 1) && a.aposta() >= apostaAtual);

        if (completa)
        {

            proximaFase();
            return;

        }

        passarVez(proximo(i, true));

    }

    private void recolherApostas()
    {

        for (Assento a : assentos)
        {

            if (a == null) continue;

            pote += a.aposta();
            a.jogador.setApostaNaRodada(0);
            a.agiu = false;

        }

        apostaAtual = 0;
        minRaise = bigBlind;

    }

    private void proximaFase()
    {

        recolherApostas();

        for (Assento a : assentos)
        {

            if (a != null && !a.desistiu() && !a.allin) a.ultimaAcao = null;

        }

        switch (fase)
        {

            case "pre-flop" -> {
                fase = "flop";
                puxar(); // queima uma carta, como em GerenciarJogo
                mesa.add(puxar());
                mesa.add(puxar());
                mesa.add(puxar());
            }

            case "flop" -> { fase = "turn"; puxar(); mesa.add(puxar()); }

            case "turn" -> { fase = "river"; puxar(); mesa.add(puxar()); }

            default -> { showdown(); return; }

        }

        log(fase.toUpperCase() + ": " + String.join(", ", mesa));

        int podem = 0;

        for (Assento a : assentos) if (a != null && !a.desistiu() && !a.allin) podem++;

        if (podem <= 1)
        {

            // Ninguém mais pode apostar: abre as próximas cartas sozinho
            vezDe = -1;
            emitir();
            agendar(this::proximaFase, 1400);
            return;

        }

        passarVez(proximo(dealer, true));

    }

    /** Showdown com side pots: cada "camada" de aposta é disputada só por quem cobriu aquele valor. */
    private void showdown()
    {

        Map<Integer, int[]> forca = new LinkedHashMap<>();

        for (int j = 0; j < maxJogadores; j++)
        {

            Assento a = assentos[j];

            if (a == null || a.desistiu()) continue;

            List<String> cartas = new ArrayList<>(a.jogador.getCartas());
            cartas.addAll(mesa);
            int[] m = AvaliadorMaos.melhorMao(cartas);
            a.maoNome = AvaliadorMaos.nome(m);
            forca.put(j, m);

        }

        TreeSet<Integer> niveis = new TreeSet<>();

        for (Assento a : assentos) if (a != null && a.contribuicao > 0) niveis.add(a.contribuicao);

        int anterior = 0;
        int[] melhorGeral = null;

        for (int nivel : niveis)
        {

            int fatia = 0;

            for (Assento a : assentos)
            {

                if (a != null) fatia += Math.min(a.contribuicao, nivel) - Math.min(a.contribuicao, anterior);

            }

            List<Integer> elegiveis = new ArrayList<>();

            for (int j : forca.keySet()) if (assentos[j].contribuicao >= nivel) elegiveis.add(j);

            if (elegiveis.isEmpty()) elegiveis.addAll(forca.keySet());

            int[] melhor = null;
            List<Integer> vencedores = new ArrayList<>();

            for (int j : elegiveis)
            {

                int c = melhor == null ? 1 : AvaliadorMaos.comparar(forca.get(j), melhor);

                if (c > 0) { melhor = forca.get(j); vencedores.clear(); vencedores.add(j); }
                else if (c == 0) vencedores.add(j);

            }

            int parte = fatia / vencedores.size();
            int resto = fatia % vencedores.size();

            for (int k = 0; k < vencedores.size(); k++)
            {

                ganhos.merge(vencedores.get(k), parte + (k == 0 ? resto : 0), Integer::sum);

            }

            if (melhorGeral == null || AvaliadorMaos.comparar(melhor, melhorGeral) > 0) melhorGeral = melhor;

            anterior = nivel;

        }

        maoVencedora = melhorGeral == null ? null : AvaliadorMaos.nome(melhorGeral);
        finalizar(true);

    }

    private void finalizar(boolean revelarCartas)
    {

        fase = "showdown";
        vezDe = -1;
        revelar = revelarCartas;

        for (Map.Entry<Integer, Integer> e : ganhos.entrySet())
        {

            Assento a = assentos[e.getKey()];
            a.jogador.setFichas(a.fichas() + e.getValue());
            a.vencedor = true;

        }

        log(textoResultado(null));
        emitir();
        agendar(this::novaMao, 5000);

    }

    private String textoResultado(Cliente para)
    {

        if (ganhos.isEmpty()) return null;

        List<String> partes = new ArrayList<>();
        boolean iguais = ganhos.values().stream().distinct().count() == 1;
        List<String> nomes = new ArrayList<>();

        for (Map.Entry<Integer, Integer> e : ganhos.entrySet())
        {

            boolean voce = para != null && para.assento == e.getKey();
            String n = voce ? "Você" : assentos[e.getKey()].nome();
            nomes.add(n);
            partes.add(n + " ganhou " + fmt(e.getValue()));

        }

        String mao = (revelar && maoVencedora != null) ? " com " + maoVencedora : "";

        if (ganhos.size() == 1)
        {

            String n = nomes.get(0);
            return n + " ganhou " + fmt(pote) + mao + (n.equals("Você") ? "!" : "");

        }

        if (iguais) return String.join(" e ", nomes) + " dividiram " + fmt(pote) + mao;

        return String.join(" · ", partes) + mao;

    }

    /* ============================== Bots ============================== */

    private void jogarBot(int i)
    {

        Assento a = assentos[i];
        Bot bot = (Bot) a.jogador;
        int paraPagar = apostaAtual - a.aposta();
        int poteTotal = pote;

        for (Assento o : assentos) if (o != null) poteTotal += o.aposta();

        AcoesJogo decisao;

        try
        {

            // O Bot do projeto trata "apostaAtual" como o quanto falta pagar
            decisao = bot.decisao(bot, mesa, paraPagar, bot.getFichas(), poteTotal);

        }

        catch (RuntimeException e)
        {

            decisao = new AcoesJogo(paraPagar == 0 ? TipoJogadas.CHECK : TipoJogadas.FOLD, 0);

        }

        TipoJogadas tipo = decisao.getTipo();

        if (tipo == TipoJogadas.RAISE)
        {

            executar(i, "aumentar", a.aposta() + paraPagar + Math.max(decisao.getValor(), minRaise));

        }

        else if (tipo == TipoJogadas.CALL)
        {

            executar(i, "pagar", 0);

        }

        else if (tipo == TipoJogadas.CHECK || paraPagar == 0)
        {

            executar(i, "passar", 0); // nunca desiste quando pode passar de graça

        }

        else
        {

            executar(i, "desistir", 0);

        }

    }

    /* ======================= Estado para o navegador ======================= */

    private void agendar(Runnable r, long ms)
    {

        if (tarefa != null) tarefa.cancel(false);

        final int meu = ++token;

        tarefa = AGENDADOR.schedule(() -> {
            synchronized (this)
            {

                if (meu != token) return;

                try { r.run(); }
                catch (RuntimeException e) { e.printStackTrace(); }

            }
        }, ms, TimeUnit.MILLISECONDS);

    }

    private void log(String texto)
    {

        if (texto == null) return;

        for (Assento a : assentos)
        {

            if (a != null && a.cliente != null && a.conectado) a.cliente.enviar(Map.of("tipo", "log", "texto", texto));

        }

    }

    private void emitir()
    {

        for (Assento a : assentos)
        {

            if (a != null && a.cliente != null && a.conectado)
            {

                a.cliente.enviar(Map.of("tipo", "estado", "estado", estadoPara(a.cliente)));

            }

        }

    }

    private Map<String, Object> estadoPara(Cliente cliente)
    {

        Map<String, Object> e = new LinkedHashMap<>();
        Map<String, Object> sala = new LinkedHashMap<>();
        sala.put("id", id);
        sala.put("nome", nome);
        sala.put("smallBlind", smallBlind);
        sala.put("bigBlind", bigBlind);

        e.put("sala", sala);
        e.put("fase", fase);
        e.put("pote", pote);
        e.put("cartasMesa", mesa.stream().map(MesaOnline::paraFront).toList());
        e.put("apostaAtual", apostaAtual);
        e.put("minRaise", minRaise == 0 ? bigBlind : minRaise);
        e.put("dealer", dealer);
        e.put("vezDe", vezDe);
        e.put("prazoAcao", prazo);
        e.put("tempoAcao", TEMPO_ACAO);
        e.put("meuAssento", cliente.assento);
        e.put("resultado", fase.equals("showdown") ? textoResultado(cliente) : null);

        List<Object> lista = new ArrayList<>();

        for (int i = 0; i < maxJogadores; i++)
        {

            Assento a = assentos[i];
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("assento", i);

            if (a == null)
            {

                s.put("vazio", true);
                lista.add(s);
                continue;

            }

            boolean voce = a.cliente == cliente;
            List<String> cartas = a.jogador.getCartas();
            boolean ver = voce || (revelar && !a.desistiu());

            s.put("nome", a.nome());
            s.put("fichas", a.fichas());
            s.put("aposta", a.aposta());
            s.put("cartas", cartas.isEmpty() ? List.of() : ver ? cartas.stream().map(MesaOnline::paraFront).toList() : List.of("??", "??"));
            s.put("desistiu", a.desistiu());
            s.put("allin", a.allin);
            s.put("ultimaAcao", a.ultimaAcao);
            s.put("voce", voce);
            s.put("vencedor", a.vencedor);
            s.put("maoNome", revelar ? a.maoNome : null);
            lista.add(s);

        }

        e.put("assentos", lista);

        return e;

    }

    /** Resumo usado pelo lobby (GET /api/salas). */
    public synchronized Map<String, Object> resumo()
    {

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("nome", nome);
        m.put("smallBlind", smallBlind);
        m.put("bigBlind", bigBlind);
        m.put("buyIn", buyIn);
        m.put("maxJogadores", maxJogadores);
        m.put("jogadores", sentados());
        m.put("emJogo", !fase.equals("aguardando"));
        m.put("privada", privada);

        return m;

    }

    /** "10 Copas" (formato do back) -> "Th" (formato do front). */
    static String paraFront(String carta)
    {

        int v = Cartas.obterValor(carta);
        String valor = switch (v) { case 10 -> "T"; case 11 -> "J"; case 12 -> "Q"; case 13 -> "K"; case 14 -> "A"; default -> String.valueOf(v); };
        String naipe = switch (Cartas.obterNipe(carta)) { case "Ouros" -> "d"; case "Copas" -> "h"; case "Paus" -> "c"; default -> "s"; };

        return valor + naipe;

    }

    private static String fmt(int n)
    {

        return FORMATO.format(n);

    }

}
