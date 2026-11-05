package br.com.poker.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import br.com.poker.enums.TipoJogadas;

public class Bot extends Jogador{
    
    private String nome = "";
    private int dificuldade = 0;
    private Random random = new Random();

    private static final String[] Nome_Bots = {
        "PokerFace Bill", "Dona Dinah", "Shark Alana", "Bluff Master", 
        "Bob das Fichas", "Mesa Redonda", "Cyborg Fold", "All-in Igor", 
        "Gatuno do Pote", "Texas Kid", "Rainha do Blefe", "Sortudo VIP"
    };

    public Bot(String nome, int dificuldade)
    {
        
        super(nome);

        this.nome = nome;
        this.dificuldade = dificuldade;

    }

    public Bot(int dificuldade) 
    {

        this(Nome_Bots[new Random().nextInt(Nome_Bots.length)] + ((dificuldade == 3) ? " (Difícil)" : (dificuldade == 2) ? " (Médio)" : " (Fácil)"), dificuldade);

    }

    public String getNome()
    {

        return nome;

    }

    public AcoesJogo decisao(Jogador botJogador, List<String> cartasMesa, int apostaAtual, int fichasDoBot, int poteAtual) 
    {

        if (cartasMesa.isEmpty())
        {

            return preFlop(botJogador.getCartas(), apostaAtual, fichasDoBot);

        }

        return posFlop(botJogador.getCartas(), cartasMesa, apostaAtual, fichasDoBot, poteAtual);

    }
    
    private int calcularPontosPre(List<String> cartasMao)
    {

        int valor_carta_1 = Cartas.obterValor(cartasMao.get(0));
        int valor_carta_2 = Cartas.obterValor(cartasMao.get(1));

        String nipe_carta_1 = Cartas.obterNipe(cartasMao.get(0));
        String nipe_carta_2 = Cartas.obterNipe(cartasMao.get(1));

        int cartaMaior = Math.max(valor_carta_1, valor_carta_2);
        int cartaMenor = Math.min(valor_carta_1, valor_carta_2);

        int pontos = 0;

        if(cartaMaior == 14)
        {

            pontos += 20;

        }

        else if(cartaMaior >= 11)
        {

            pontos += 15;

        }

        else
        {

            pontos += cartaMaior;

        }


        if(valor_carta_1 == valor_carta_2)
        {

            pontos += 25;

            if(cartaMaior >= 11)
            {

                pontos += 20;

            }

        }


        if(nipe_carta_1.equals(nipe_carta_2))
        {

            pontos += 8;

        }


        int possivelSequencia = cartaMaior - cartaMenor;

        if(possivelSequencia == 1)
        {

            pontos += 10;

        }

        else if(possivelSequencia == 2)
        {

            pontos += 5;

        }

        return pontos;

    }

    private AcoesJogo preFlop(List<String> cartasMao, int apostaAtual, int fichasDoBot)
    {

        int forcaDaMao = calcularPontosPre(cartasMao);

        if(apostaAtual == 0)
        {

            if(forcaDaMao > 40 && this.dificuldade == 2)
            {

                int primeiraAposta = (this.dificuldade == 3) ? 80 : 40;

                return new AcoesJogo(TipoJogadas.RAISE, Math.min(primeiraAposta, fichasDoBot));

            }

            return new AcoesJogo(TipoJogadas.CHECK, 0);

        }

        double dificuldadeAposta = ((double) (apostaAtual / fichasDoBot)) * 100;

        int risco = (int) (dificuldadeAposta * 0.8);

        if(dificuldadeAposta < 3 && forcaDaMao < 25)
        {

            int chanceBlefar = (this.dificuldade == 3) ? 18 : (this.dificuldade == 2) ? 8 : 3;

            if(random.nextInt(100) < chanceBlefar)
            {

                double multiplicarPot = (this.dificuldade == 3) ? 3.5 : 2.0;

                int valorBlefe = (int) (apostaAtual * multiplicarPot);

                return new AcoesJogo(TipoJogadas.RAISE, valorBlefe);

            }

        }

        if(this.dificuldade == 1)
        {

            if(dificuldadeAposta > 15 && forcaDaMao < 50)
            {

                return new AcoesJogo(TipoJogadas.FOLD, 0);

            }

            if(forcaDaMao > (30 + risco))
            {

                return new AcoesJogo(TipoJogadas.CALL, 0);

            }

            return new AcoesJogo(TipoJogadas.FOLD, 0);

        }


        else if(this.dificuldade == 2)
        {

            if(dificuldadeAposta < 30 && forcaDaMao > 50)
            {

                int valorAumento = (int) (apostaAtual * 1 + random.nextInt(2));

                return new AcoesJogo(TipoJogadas.RAISE, Math.min(valorAumento, fichasDoBot));

            }

            if(forcaDaMao > (20 + risco))
            {

                return new AcoesJogo(TipoJogadas.CALL, 0);

            }

            return new AcoesJogo(TipoJogadas.FOLD, 0);

        }

        else if(this.dificuldade == 3)
        {

            if(forcaDaMao > 55)
            {

                if(dificuldadeAposta > 50)
                {

                    return new AcoesJogo(TipoJogadas.CALL, 0);

                }

                int valorAumento = (int) (apostaAtual * 2 + random.nextInt(2));

                return new AcoesJogo(TipoJogadas.RAISE, Math.min(valorAumento, fichasDoBot));

            }

            if(forcaDaMao > (15 + risco))
            {

                return new AcoesJogo(TipoJogadas.CALL, 0);

            }

            return new AcoesJogo(TipoJogadas.FOLD, 0);

        }

        return new AcoesJogo(TipoJogadas.FOLD, 0);
    
    }


    private int calcularPontosPos(List<String> cartasMao, List<String> cartasMesa)
    {

        List<String> todasCartas = new ArrayList<>(cartasMao);

        todasCartas.addAll(cartasMesa);

        int[] contagemValor = new int[15];

        Map<String, Integer> contagemNaipe = new HashMap<>();

        for(int i = 0; i < todasCartas.size(); i++)
        {

            String carta = todasCartas.get(i);

            int valor = Cartas.obterValor(carta);
            String naipe = Cartas.obterNipe(carta);

            contagemValor[valor]++;
            contagemNaipe.put(naipe, contagemNaipe.getOrDefault(naipe, 0) + 1);

        }

        List<Integer> quantidadesNaipes = new ArrayList<>(contagemNaipe.values());


        int par = 0;
        int trinca = 0;
        int quadra = 0;
        boolean flush = false;
        boolean projetoFlush = false;

        for(int i = 0; i < contagemValor.length; i++)
        {

            int quantidade = contagemValor[i];

            if(quantidade == 2)
            {

                par++;

            }

            if(quantidade == 3)
            {

                trinca++;

            }

            if(quantidade == 4)
            {

                quadra++;

            }

        }

        for(int i = 0; i < quantidadesNaipes.size(); i++)
        {

            int quantidade = quantidadesNaipes.get(i);

            if(quantidade == 5)
            {

                flush = true;

            }

            if(quantidade == 4)
            {

                projetoFlush = true;

            }

        }

        int pontosBase = 0;

        if(projetoFlush == true && cartasMesa.size() < 5)
        {

            pontosBase += 20;

        }

        if(quadra > 0 || (trinca > 0 && par > 0) || flush)
        {

            pontosBase += 90;

        }

        else if(trinca > 0)
        {

            pontosBase += 70;

        }

        else if(par >= 2)
        {

            pontosBase += 55;

        }

        else if(par == 1)
        {

            pontosBase += 35;

        }

        else
        {

            int maiorCartaMao = Math.max(Cartas.obterValor(cartasMao.get(0)), Cartas.obterValor(cartasMao.get(1)));

            return maiorCartaMao;
            
        }

        int quantidadeCartasMesa = cartasMesa.size();

        if(quantidadeCartasMesa == 4)
        {

            if(pontosBase <= 35)
            {

                pontosBase -= 5;

            }

        }

        else if(quantidadeCartasMesa == 5)
        {

            if(pontosBase == 35)
            {

                pontosBase -= 10;

            }

            else if(pontosBase < 35)
            {

                pontosBase = 0;

            }

        }

        return pontosBase;

    }

    private AcoesJogo posFlop(List<String> cartasMao, List<String> cartasMesa, int apostaAtual, int fichasDoBot, int poteAtual)
    {

        int forcaDaMao = calcularPontosPos(cartasMao, cartasMesa);

        if(apostaAtual == 0)
        {

            if(forcaDaMao >= 70)
            {

                int apostaPensada = (int) (poteAtual * 0.5);

                int apostaFinal = Math.max(40, apostaPensada);

                return new AcoesJogo(TipoJogadas.RAISE, Math.min(apostaFinal, fichasDoBot));

            }

            return new AcoesJogo(TipoJogadas.CHECK, 0);

        }

        double pesoDasFichas = ((double) apostaAtual / fichasDoBot) * 100;

        double propocionalAoPote = ((double) apostaAtual / (poteAtual + apostaAtual) * 100);


        int bonusPorPote = (propocionalAoPote < 20) ? 15 : 0;

        int risco = (int) (pesoDasFichas * 0.7) - bonusPorPote;


        if(forcaDaMao < 55 && propocionalAoPote < 15)
        {

            int chanceBlefar = (this.dificuldade == 3) ? 12 : (this.dificuldade == 2) ? 6 : 2;

            if(random.nextInt(100) < chanceBlefar)
            {

                double multiplicarPot = (this.dificuldade == 3) ? 0.7 : 0.5;

                int valorBlefe = apostaAtual + (int) (poteAtual * multiplicarPot);

                return new AcoesJogo(TipoJogadas.RAISE, Math.min(valorBlefe, fichasDoBot));

            }

            if(this.dificuldade == 1)
            {

                if(pesoDasFichas > 25 && forcaDaMao < 55)
                {

                    return new AcoesJogo(TipoJogadas.FOLD, 0);

                }

                if(forcaDaMao > (30 + risco))
                {

                    return new AcoesJogo(TipoJogadas.CALL, 0);

                }

                return new AcoesJogo(TipoJogadas.FOLD, 0);

            }

            else if(this.dificuldade == 2)
            {

                if(pesoDasFichas > 40 && forcaDaMao < 70)
                {

                    int valorAumento = (int) (apostaAtual + (poteAtual * 0.5));

                    return new AcoesJogo(TipoJogadas.RAISE, Math.min(valorAumento, fichasDoBot));

                }

                if(forcaDaMao > (30 + risco))
                {

                    return new AcoesJogo(TipoJogadas.CALL, 0);

                }

                return new AcoesJogo(TipoJogadas.FOLD, 0);

            }

            else if(this.dificuldade == 3)
            {

                if(forcaDaMao < 90)
                {

                    if(pesoDasFichas > 60)
                    {

                        return new AcoesJogo(TipoJogadas.CALL, 0);

                    }

                    int apostaSuper = apostaAtual + poteAtual;

                    return new AcoesJogo(TipoJogadas.RAISE, Math.min(apostaSuper, fichasDoBot));

                }

                if(forcaDaMao > (15 + (risco * 0.5)))
                {

                    return new AcoesJogo(TipoJogadas.CALL, 0);

                }

                return new AcoesJogo(TipoJogadas.FOLD, 0);

            }

            return new AcoesJogo(TipoJogadas.FOLD, 0);

        }

        return new AcoesJogo(TipoJogadas.FOLD, 0);

    }

}
