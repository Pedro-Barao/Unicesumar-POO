package br.com.poker.web;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * Servidor web do Poker — só usa o JDK (nenhuma biblioteca).
 *
 * Serve o front-end (pasta poker-frontend) e a API:
 *   GET  /api/salas                      lista de salas
 *   GET  /api/salas/{id}                 uma sala
 *   POST /api/salas                      cria sala
 *   GET  /api/salas/{id}/eventos?nome=X  entra na mesa e recebe o estado em tempo real (SSE)
 *   POST /api/salas/{id}/acao            { cliente, acao, valor }
 *   POST /api/salas/{id}/chat            { cliente, texto }
 *   POST /api/salas/{id}/sair            { cliente }
 *
 * Rodar:  java -cp bin br.com.poker.web.ServidorWeb [porta] [pastaDoFront]
 * Depois abra http://localhost:8080
 */
public class ServidorWeb {

    private static final Map<String, MesaOnline> SALAS = new ConcurrentHashMap<>();
    private static final Map<String, Cliente> CLIENTES = new ConcurrentHashMap<>();
    private static Path pastaFront;

    public static void main(String[] args) throws IOException
    {

        int porta = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        pastaFront = localizarFront(args.length > 1 ? args[1] : null);

        criarSalasPadrao();

        HttpServer servidor = HttpServer.create(new InetSocketAddress(porta), 0);
        servidor.createContext("/api/", ServidorWeb::api);
        servidor.createContext("/", ServidorWeb::arquivoEstatico);
        servidor.setExecutor(Executors.newCachedThreadPool()); // cada SSE ocupa uma thread
        servidor.start();

        System.out.println("=======================================================");
        System.out.println("  Poker Texas Hold'em rodando em http://localhost:" + porta);
        System.out.println("  Front-end: " + pastaFront.toAbsolutePath());
        System.out.println("=======================================================");

    }

    private static Path localizarFront(String informado)
    {

        List<String> candidatos = new ArrayList<>();

        if (informado != null) candidatos.add(informado);

        candidatos.add("poker-frontend");
        candidatos.add("../poker-frontend");
        candidatos.add("Poker/poker-frontend");

        for (String c : candidatos)
        {

            Path p = Path.of(c);

            if (Files.isRegularFile(p.resolve("index.html"))) return p.toAbsolutePath().normalize();

        }

        throw new IllegalStateException("Não encontrei a pasta poker-frontend. Rode a partir da pasta do projeto ou passe o caminho como 2º argumento.");

    }

    private static void criarSalasPadrao()
    {

        adicionar(new MesaOnline("iniciantes", "Mesa dos Iniciantes", 5, 10, 1000, 6, false, 3, 1));
        adicionar(new MesaOnline("copacabana", "Copacabana", 10, 20, 2000, 8, false, 5, 2));
        adicionar(new MesaOnline("heads-up", "Heads-Up Arena", 25, 50, 5000, 2, false, 1, 3));
        adicionar(new MesaOnline("high-rollers", "High Rollers", 100, 200, 20000, 6, false, 4, 3));
        adicionar(new MesaOnline("treino", "Sala de Treino", 1, 2, 200, 4, false, 2, 1));

    }

    private static void adicionar(MesaOnline m)
    {

        SALAS.put(m.id, m);

    }

    /* ================================ API ================================ */

    private static void api(HttpExchange ex) throws IOException
    {

        try
        {

            cors(ex);
            String metodo = ex.getRequestMethod();

            if (metodo.equals("OPTIONS"))
            {

                ex.sendResponseHeaders(204, -1);
                ex.close();
                return;

            }

            String[] partes = ex.getRequestURI().getPath().replaceAll("/+$", "").split("/");
            // ["", "api", "salas", id?, subrota?]

            if (partes.length < 3 || !partes[2].equals("salas"))
            {

                responder(ex, 404, Map.of("erro", "Rota não encontrada"));
                return;

            }

            if (partes.length == 3)
            {

                if (metodo.equals("GET")) listarSalas(ex);
                else if (metodo.equals("POST")) criarSala(ex);
                else responder(ex, 405, Map.of("erro", "Método não permitido"));

                return;

            }

            MesaOnline mesa = SALAS.get(partes[3]);

            if (mesa == null)
            {

                responder(ex, 404, Map.of("erro", "Sala não encontrada"));
                return;

            }

            if (partes.length == 4)
            {

                responder(ex, 200, mesa.resumo());
                return;

            }

            switch (partes[4])
            {

                case "eventos" -> eventos(ex, mesa);
                case "acao" -> {
                    Map<String, Object> corpo = corpo(ex);
                    Cliente c = cliente(corpo, mesa);
                    if (c != null) mesa.acao(c, texto(corpo, "acao", ""), (int) numero(corpo, "valor", 0));
                    responder(ex, 200, Map.of("ok", c != null));
                }
                case "chat" -> {
                    Map<String, Object> corpo = corpo(ex);
                    Cliente c = cliente(corpo, mesa);
                    if (c != null) mesa.chat(c, texto(corpo, "texto", ""));
                    responder(ex, 200, Map.of("ok", c != null));
                }
                case "sair" -> {
                    Map<String, Object> corpo = corpo(ex);
                    Cliente c = cliente(corpo, mesa);
                    if (c != null) { mesa.sair(c); CLIENTES.remove(c.id); }
                    responder(ex, 200, Map.of("ok", true));
                }
                default -> responder(ex, 404, Map.of("erro", "Rota não encontrada"));

            }

        }

        catch (IllegalArgumentException e)
        {

            responder(ex, 400, Map.of("erro", e.getMessage() == null ? "Requisição inválida" : e.getMessage()));

        }

        catch (RuntimeException e)
        {

            e.printStackTrace();
            responder(ex, 500, Map.of("erro", "Erro interno no servidor"));

        }

    }

    private static void listarSalas(HttpExchange ex) throws IOException
    {

        List<Object> lista = new ArrayList<>();

        for (MesaOnline m : SALAS.values()) lista.add(m.resumo());

        lista.sort((a, b) -> Integer.compare(
            (int) ((Map<?, ?>) a).get("bigBlind"), (int) ((Map<?, ?>) b).get("bigBlind")));

        responder(ex, 200, lista);

    }

    private static void criarSala(HttpExchange ex) throws IOException
    {

        Map<String, Object> d = corpo(ex);

        String nome = texto(d, "nome", "").trim();

        if (nome.isEmpty()) throw new IllegalArgumentException("Informe o nome da sala.");
        if (nome.length() > 28) nome = nome.substring(0, 28);

        int bb = (int) Math.max(2, numero(d, "bigBlind", 10));
        int sb = (int) Math.max(1, Math.min(bb, numero(d, "smallBlind", bb / 2)));
        int buyIn = (int) Math.max(bb * 10L, numero(d, "buyIn", bb * 100L));
        int max = (int) Math.max(2, Math.min(8, numero(d, "maxJogadores", 8)));
        int bots = (int) Math.max(0, Math.min(max - 1, numero(d, "bots", 3)));
        int dif = (int) Math.max(1, Math.min(3, numero(d, "dificuldade", 2)));
        boolean privada = Boolean.TRUE.equals(d.get("privada"));

        String id = "sala-" + Long.toString(System.currentTimeMillis(), 36);
        MesaOnline mesa = new MesaOnline(id, nome, sb, bb, buyIn, max, privada, bots, dif);
        adicionar(mesa);

        responder(ex, 201, mesa.resumo());

    }

    /** Server-Sent Events: a conexão fica aberta e o servidor empurra cada mudança da mesa. */
    private static void eventos(HttpExchange ex, MesaOnline mesa) throws IOException
    {

        String nome = parametro(ex, "nome");

        ex.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.sendResponseHeaders(200, 0);
        OutputStream os = ex.getResponseBody();

        Cliente cliente;

        try
        {

            cliente = mesa.entrar(nome);

        }

        catch (IllegalStateException cheia)
        {

            os.write(("data: " + Json.escrever(Map.of("tipo", "erro", "texto", cheia.getMessage())) + "\n\n").getBytes(StandardCharsets.UTF_8));
            os.close();
            return;

        }

        CLIENTES.put(cliente.id, cliente);

        try
        {

            while (cliente.ativo)
            {

                String msg = cliente.fila.poll(15, TimeUnit.SECONDS);
                String bloco = (msg == null) ? ": ping\n\n" : "data: " + msg + "\n\n";
                os.write(bloco.getBytes(StandardCharsets.UTF_8));
                os.flush();

            }

        }

        catch (IOException | InterruptedException desconectou)
        {

            // navegador fechou a aba ou caiu a conexão

        }

        finally
        {

            mesa.sair(cliente);
            CLIENTES.remove(cliente.id);

            try { os.close(); } catch (IOException ignorar) { }

        }

    }

    /* ========================== Arquivos do front ========================== */

    private static final Map<String, String> TIPOS = Map.of(
        "html", "text/html; charset=utf-8",
        "css", "text/css; charset=utf-8",
        "js", "application/javascript; charset=utf-8",
        "json", "application/json; charset=utf-8",
        "svg", "image/svg+xml",
        "png", "image/png",
        "jpg", "image/jpeg",
        "ico", "image/x-icon"
    );

    private static void arquivoEstatico(HttpExchange ex) throws IOException
    {

        String caminho = URLDecoder.decode(ex.getRequestURI().getPath(), StandardCharsets.UTF_8);

        if (caminho.equals("/") || caminho.isEmpty()) caminho = "/index.html";

        Path arquivo = pastaFront.resolve(caminho.substring(1)).normalize();

        if (!arquivo.startsWith(pastaFront) || !Files.isRegularFile(arquivo))
        {

            byte[] b = "404 - Não encontrado".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            ex.sendResponseHeaders(404, b.length);
            ex.getResponseBody().write(b);
            ex.close();
            return;

        }

        String nome = arquivo.getFileName().toString();
        String ext = nome.contains(".") ? nome.substring(nome.lastIndexOf('.') + 1).toLowerCase() : "";
        byte[] b = Files.readAllBytes(arquivo);

        ex.getResponseHeaders().set("Content-Type", TIPOS.getOrDefault(ext, "application/octet-stream"));
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.sendResponseHeaders(200, b.length);
        ex.getResponseBody().write(b);
        ex.close();

    }

    /* ============================== Utilidades ============================== */

    private static void cors(HttpExchange ex)
    {

        // Permite usar o front em outro servidor (ex.: Live Server do VS Code na porta 5500)
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

    }

    private static void responder(HttpExchange ex, int status, Object corpo) throws IOException
    {

        byte[] b = Json.escrever(corpo).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(status, b.length);
        ex.getResponseBody().write(b);
        ex.close();

    }

    private static Map<String, Object> corpo(HttpExchange ex) throws IOException
    {

        String texto = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);

        return Json.lerObjeto(texto);

    }

    private static Cliente cliente(Map<String, Object> corpo, MesaOnline mesa)
    {

        Cliente c = CLIENTES.get(texto(corpo, "cliente", ""));

        return (c != null && c.mesa == mesa) ? c : null;

    }

    private static String texto(Map<String, Object> m, String chave, String padrao)
    {

        Object v = m.get(chave);

        return v == null ? padrao : v.toString();

    }

    private static double numero(Map<String, Object> m, String chave, double padrao)
    {

        Object v = m.get(chave);

        if (v instanceof Number n) return n.doubleValue();

        try { return v == null ? padrao : Double.parseDouble(v.toString()); }
        catch (NumberFormatException e) { return padrao; }

    }

    private static String parametro(HttpExchange ex, String nome)
    {

        String q = ex.getRequestURI().getRawQuery();

        if (q == null) return null;

        for (String par : q.split("&"))
        {

            int i = par.indexOf('=');
            String k = i < 0 ? par : par.substring(0, i);

            if (URLDecoder.decode(k, StandardCharsets.UTF_8).equals(nome))
            {

                return i < 0 ? "" : URLDecoder.decode(par.substring(i + 1), StandardCharsets.UTF_8);

            }

        }

        return null;

    }

}
