package br.com.poker.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON mínimo (sem bibliotecas): converte Map/List/String/Number/Boolean e lê objetos simples. */
public final class Json {

    private Json() { }

    /* ----------------------------- Escrita ----------------------------- */

    public static String escrever(Object o)
    {

        StringBuilder sb = new StringBuilder();
        escrever(o, sb);

        return sb.toString();

    }

    private static void escrever(Object o, StringBuilder sb)
    {

        if (o == null)
        {

            sb.append("null");

        }

        else if (o instanceof String s)
        {

            sb.append('"');

            for (char c : s.toCharArray())
            {

                switch (c)
                {

                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    default -> {
                        if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                        else sb.append(c);
                    }

                }

            }

            sb.append('"');

        }

        else if (o instanceof Number || o instanceof Boolean)
        {

            sb.append(o);

        }

        else if (o instanceof Map<?, ?> m)
        {

            sb.append('{');
            boolean primeiro = true;

            for (Map.Entry<?, ?> e : m.entrySet())
            {

                if (!primeiro) sb.append(',');
                primeiro = false;
                escrever(String.valueOf(e.getKey()), sb);
                sb.append(':');
                escrever(e.getValue(), sb);

            }

            sb.append('}');

        }

        else if (o instanceof Iterable<?> it)
        {

            sb.append('[');
            boolean primeiro = true;

            for (Object x : it)
            {

                if (!primeiro) sb.append(',');
                primeiro = false;
                escrever(x, sb);

            }

            sb.append(']');

        }

        else
        {

            escrever(o.toString(), sb);

        }

    }

    /* ------------------------------ Leitura ----------------------------- */

    public static Object ler(String texto)
    {

        Leitor l = new Leitor(texto);
        l.espacos();
        Object v = l.valor();

        return v;

    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> lerObjeto(String texto)
    {

        if (texto == null || texto.isBlank())
        {

            return new LinkedHashMap<>();

        }

        Object v = ler(texto);

        if (!(v instanceof Map))
        {

            throw new IllegalArgumentException("JSON deve ser um objeto");

        }

        return (Map<String, Object>) v;

    }

    private static final class Leitor
    {

        private final String s;
        private int i;

        Leitor(String s) { this.s = s; }

        void espacos()
        {

            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;

        }

        char atual()
        {

            if (i >= s.length()) throw new IllegalArgumentException("JSON incompleto");

            return s.charAt(i);

        }

        Object valor()
        {

            char c = atual();

            if (c == '{') return objeto();
            if (c == '[') return lista();
            if (c == '"') return texto();
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            if (s.startsWith("null", i)) { i += 4; return null; }

            return numero();

        }

        Map<String, Object> objeto()
        {

            Map<String, Object> m = new LinkedHashMap<>();
            i++;
            espacos();

            if (atual() == '}') { i++; return m; }

            while (true)
            {

                espacos();
                String chave = texto();
                espacos();
                if (atual() != ':') throw new IllegalArgumentException("Esperado ':'");
                i++;
                espacos();
                m.put(chave, valor());
                espacos();
                char c = atual();
                i++;
                if (c == '}') return m;
                if (c != ',') throw new IllegalArgumentException("Esperado ',' ou '}'");

            }

        }

        List<Object> lista()
        {

            List<Object> l = new ArrayList<>();
            i++;
            espacos();

            if (atual() == ']') { i++; return l; }

            while (true)
            {

                espacos();
                l.add(valor());
                espacos();
                char c = atual();
                i++;
                if (c == ']') return l;
                if (c != ',') throw new IllegalArgumentException("Esperado ',' ou ']'");

            }

        }

        String texto()
        {

            if (atual() != '"') throw new IllegalArgumentException("Esperado texto");
            i++;
            StringBuilder sb = new StringBuilder();

            while (true)
            {

                char c = atual();
                i++;

                if (c == '"') return sb.toString();

                if (c == '\\')
                {

                    char e = atual();
                    i++;

                    switch (e)
                    {

                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'r' -> sb.append('\r');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> { sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; }
                        default -> sb.append(e);

                    }

                }

                else
                {

                    sb.append(c);

                }

            }

        }

        Number numero()
        {

            int ini = i;

            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;

            String n = s.substring(ini, i);

            if (n.isEmpty()) throw new IllegalArgumentException("Valor JSON inválido");

            if (n.contains(".") || n.contains("e") || n.contains("E")) return Double.parseDouble(n);

            return Long.parseLong(n);

        }

    }

}
