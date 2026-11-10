package br.com.poker.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Avalia mãos de Texas Hold'em usando o formato de carta do projeto ("10 Copas", "A Espadas").
 *
 * O resultado de uma avaliação é um vetor de inteiros: a primeira posição é a categoria
 * (0 = carta alta ... 8 = straight flush) e as seguintes são os desempates.
 * Use {@link #comparar(int[], int[])} para saber qual mão é maior.
 */
public final class AvaliadorMaos {

    public static final String[] NOMES = {
        "Carta alta", "Par", "Dois pares", "Trinca", "Sequência",
        "Flush", "Full house", "Quadra", "Straight flush"
    };

    private AvaliadorMaos() { }

    /** Avalia exatamente 5 cartas. */
    public static int[] avaliar5(List<String> cartas)
    {

        int[] v = new int[5];
        boolean flush = true;
        String naipe = Cartas.obterNipe(cartas.get(0));

        for (int i = 0; i < 5; i++)
        {

            v[i] = Cartas.obterValor(cartas.get(i));

            if (!Cartas.obterNipe(cartas.get(i)).equals(naipe))
            {

                flush = false;

            }

        }

        java.util.Arrays.sort(v);
        // ordem decrescente
        for (int i = 0; i < 2; i++)
        {

            int t = v[i];
            v[i] = v[4 - i];
            v[4 - i] = t;

        }

        int[] contagem = new int[15];

        for (int x : v)
        {

            contagem[x]++;

        }

        int distintos = 0;

        for (int c : contagem)
        {

            if (c > 0)
            {

                distintos++;

            }

        }

        int sequencia = -1;

        if (distintos == 5)
        {

            if (v[0] - v[4] == 4)
            {

                sequencia = v[0];

            }

            else if (v[0] == 14 && v[1] == 5 && v[4] == 2)
            {

                sequencia = 5; // A-2-3-4-5

            }

        }

        // grupos ordenados por quantidade e depois por valor (ex.: trinca antes do par)
        List<int[]> grupos = new ArrayList<>();

        for (int valor = 14; valor >= 2; valor--)
        {

            if (contagem[valor] > 0)
            {

                grupos.add(new int[] { contagem[valor], valor });

            }

        }

        grupos.sort((a, b) -> a[0] != b[0] ? b[0] - a[0] : b[1] - a[1]);

        int[] ordem = new int[grupos.size()];

        for (int i = 0; i < ordem.length; i++)
        {

            ordem[i] = grupos.get(i)[1];

        }

        int maior = grupos.get(0)[0];
        int segundo = grupos.size() > 1 ? grupos.get(1)[0] : 0;

        if (sequencia >= 0 && flush) return new int[] { 8, sequencia };
        if (maior == 4) return comCategoria(7, ordem);
        if (maior == 3 && segundo == 2) return comCategoria(6, ordem);
        if (flush) return comCategoria(5, v);
        if (sequencia >= 0) return new int[] { 4, sequencia };
        if (maior == 3) return comCategoria(3, ordem);
        if (maior == 2 && segundo == 2) return comCategoria(2, ordem);
        if (maior == 2) return comCategoria(1, ordem);

        return comCategoria(0, v);

    }

    private static int[] comCategoria(int categoria, int[] desempate)
    {

        int[] r = new int[desempate.length + 1];
        r[0] = categoria;
        System.arraycopy(desempate, 0, r, 1, desempate.length);

        return r;

    }

    /** Positivo se a > b, negativo se a < b, zero se empate. */
    public static int comparar(int[] a, int[] b)
    {

        for (int i = 0; i < Math.max(a.length, b.length); i++)
        {

            int x = i < a.length ? a[i] : -1;
            int y = i < b.length ? b[i] : -1;

            if (x != y)
            {

                return x - y;

            }

        }

        return 0;

    }

    /** Melhor combinação de 5 cartas entre 5 e 7 cartas (mão + mesa). */
    public static int[] melhorMao(List<String> cartas)
    {

        int n = cartas.size();

        if (n < 5)
        {

            throw new IllegalArgumentException("São necessárias pelo menos 5 cartas.");

        }

        int[] melhor = null;

        for (int a = 0; a < n; a++)
            for (int b = a + 1; b < n; b++)
                for (int c = b + 1; c < n; c++)
                    for (int d = c + 1; d < n; d++)
                        for (int e = d + 1; e < n; e++)
                        {

                            int[] s = avaliar5(List.of(cartas.get(a), cartas.get(b), cartas.get(c), cartas.get(d), cartas.get(e)));

                            if (melhor == null || comparar(s, melhor) > 0)
                            {

                                melhor = s;

                            }

                        }

        return melhor;

    }

    public static String nome(int[] avaliacao)
    {

        return NOMES[avaliacao[0]];

    }

}
