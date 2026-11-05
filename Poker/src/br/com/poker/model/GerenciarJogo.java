package br.com.poker.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import br.com.poker.exception.BaralhoVazioException;

public class GerenciarJogo {
    
    private static final GerenciarJogo gerenciadorJogo = new GerenciarJogo();
    private final int limiteMesa = 5;
    private final int limiteJogadores = 8;
    private final int limiteCartasJogador = 2;
    private final int BigBlind = 10, SmallBlind = 5;
    private int dealerIndex = 0;

    private List<Jogador> jogadores = new ArrayList<>();
    private List<String> baralho = new ArrayList<>();
    private List<String> cartasMesa = new ArrayList<>();

    private Random puxarCarta = new Random();


    private GerenciarJogo()
    {

        jogadores = new ArrayList<>();

    }


    public static GerenciarJogo getInstance()
    {

        return gerenciadorJogo;

    }

    public int getLimiteJogadores()
    {

        return limiteJogadores;
        
    }

    public int getLimiteMesa()
    {

        return limiteMesa;
        
    }

    public int getLimiteCartasJogador()
    {

        return limiteCartasJogador;
        
    }

    public int getDealerIndex()
    {

        return dealerIndex;
        
    }

    public int getBigBlind()
    {

        return BigBlind;

    }

    public int getSmallBlind()
    {

        return SmallBlind;

    }

    public String identificarPosicaoBlind(int indiceDoJogador) 
    {

        int total = jogadores.size();

        if (total == 2) 
        {

            return (indiceDoJogador == dealerIndex) ? "Dealer / Small Blind" : "Big Blind";

        }

        int smallIndex = (dealerIndex + 1) % total;
        int bigIndex = (dealerIndex + 2) % total;


        if (indiceDoJogador == dealerIndex) 
        {
            
            return "Dealer";

        }

        if (indiceDoJogador == smallIndex) 
        {

            return "Small Blind";

        }

        if (indiceDoJogador == bigIndex) 
        {
            
            return "Big Blind";

        }

        return "Jogador Comum"; 

    }


    public void adicionarJogador(Jogador jogador)
    {

        if(jogadores.size() > limiteJogadores)
        {

            throw new IllegalStateException("A mesa já atingiu o limite máximo de " + limiteJogadores + " jogadores.");

        }

        this.jogadores.add(jogador);

    }

    public void adicionarBot(int dificuldade) 
    {

        if (jogadores.size() >= limiteJogadores) 
        {

            throw new IllegalStateException("A mesa já atingiu o limite máximo de " + limiteJogadores + " jogadores.");

        }

        Bot novoBot = new Bot(dificuldade);

        System.out.println("\nBot " + novoBot.getNome() + " foi adicionado a partida\n");

        this.jogadores.add(novoBot);
        
    }

    public void avancarDealer()
    {

        dealerIndex = (dealerIndex + 1) % jogadores.size();
        
    }

    public void reiniciarPartida()
    {

        dealerIndex = 0;
        cartasMesa.clear();

        for(int i = 0; i < jogadores.size(); i++)
        {

            Jogador jogador = jogadores.get(i);

            jogador.setFichas(Jogador.fichas_iniciais);
            jogador.setMaoAtual(true);
            jogador.setApostaNaRodada(0);
            jogador.getCartas().clear();

        }

    }

    public void getRemoverJogador(int id)
    {

        id -= 1;

        if(id >= jogadores.size() || id < 0)
        {

            throw new IllegalArgumentException("\nNúmero de jogador inválido!");

        }

        Jogador jogadorRemovido = jogadores.remove(id);

        System.out.println("\nO Jogador " + jogadorRemovido.getNomeJogador() + " foi removido com sucesso!\n");

    }


    public String getJogadorPorId(int id)
    {

        id -= 1;

        if(id >= jogadores.size() || id < 0)
        {

            throw new IllegalArgumentException("\nNúmero de jogador inválido!");
        }

        Jogador jogadorEncontrado = jogadores.get(id);

        return jogadorEncontrado.getNomeJogador();
        
    }

    public List<Jogador> getJogadores()
    {

        return jogadores;

    }


    public List<String> getCartasMesa() 
    { 
        
        return cartasMesa; 
    
    }


    public void iniciarJogo()
    {

        String[] naipe = {" Ouros", " Copas", " Paus", " Espadas"};
        String[] valor = {"2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A"};
        
        baralho.clear();

        cartasMesa.clear();

        for (int i = 0; i < naipe.length; i++) 
        {
            for (int o = 0; o < valor.length; o++) 
            {

                baralho.add(valor[o] + naipe[i]);

            }

        }

        for(int i = 0; i < jogadores.size(); i++)
        {

            Jogador jogador = jogadores.get(i);

            if(jogador.getFichas() > 0)
            {

                jogador.setMaoAtual(true);

            }

            else
            {

                jogador.setMaoAtual(false);
                
            }

        }

    }


    public String pegarCarta()
    {

        if(baralho.isEmpty())
        {

            throw new BaralhoVazioException("\nBaralho vazio!");

        }

        int cartaAleatoria = puxarCarta.nextInt(baralho.size());

        return baralho.remove(cartaAleatoria);

    }

    
    public void distribuirCartas()
    {

        for(int i = 0; i < jogadores.size(); i++)
        {
            
            Jogador jogador = jogadores.get(i);

            jogador.getCartas().clear();

            jogador.getCartas().add(pegarCarta());
            jogador.getCartas().add(pegarCarta());

            if(jogador.getCartas().size() > limiteCartasJogador)
            {

                throw new IllegalStateException("Limite máximo de cartas de " + limiteCartasJogador + " na mão do jogador");

            }
        
        }

    }


    public void mostrarTodasCartasMesa()
    {

        pegarCarta();

        cartasMesa.add(pegarCarta());
        cartasMesa.add(pegarCarta());
        cartasMesa.add(pegarCarta());

    }

    
    public void mostrarCartaMesa()
    {

        if(cartasMesa.size() >= limiteMesa)
        {

            throw new IllegalStateException("Limite máximo de cartas de " + limiteMesa + " na mesa puxadas");

        }

        pegarCarta();

        cartasMesa.add(pegarCarta());
        
    }

    public boolean possuiJogadorHumano() 
    {

        for (int i = 0; i < jogadores.size(); i++) 
        {

            Jogador j = jogadores.get(i);
            
            if (!(j instanceof Bot)) 
            {

                return true;
                
            }
        }
        
        return false;
    }

    public void imprimirInterface(Jogador jogador, List<String> cartasMesa, int pote, String blind)
    {

        if (jogador instanceof Bot) 
        {

            return; 

        }

        System.out.println("\n=======================================================");

        System.out.print("| Pote: " + pote + " | Você é: " + blind + " |");
        System.out.println(" | Suas Fichas: " + jogador.getFichas() + " |"); 

        System.out.println("-------------------------------------------------------");
    
        System.out.print("| ");
        
        for(int i = 0; i < 5; i++)
        {

            if(i < cartasMesa.size())
            {

                System.out.print(cartasMesa.get(i) + " | ");

            }

            else
            {

                System.out.print(" [VIRADA] |");

            }
            
        }

        System.out.println("\n-------------------------------------------------------");

        if (jogador.getCartas() != null && jogador.getCartas().size() >= 2) 
        {

            System.out.println("Suas Cartas: " + jogador.getCartas().get(0) + " e " + jogador.getCartas().get(1));

        } 
        
        else 
        {

            System.out.println("Suas Cartas: [Aguardando Distribuição]");

        }   

        System.out.println("=======================================================");

        System.out.println("1) Check\n2) Call\n3) Fold\n4) Raise");
        System.out.print("Escolha sua ação: ");

    }
    
}
