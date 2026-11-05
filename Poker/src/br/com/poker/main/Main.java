package br.com.poker.main;

import java.util.Scanner;

import br.com.poker.model.GerenciarJogo;
import br.com.poker.model.Jogador;
import br.com.poker.model.Jogo;

public class Main {

    public static void main(String[] args)
    {
    
        Scanner scanner = new Scanner(System.in);
        GerenciarJogo Partida = GerenciarJogo.getInstance();
        
        String nome = "";
        int escolha_1 = 0, escolha_2 = 0, escolha_3 = 0, escolha_4 = 0;
        do
        {

            System.out.println("\nOlá! Bem-vindo(a) ao Poker Texas Hold'em\nDeseja jogar?(1 - Sim | 2 - Não)");
            escolha_1 = scanner.nextInt();

            if(escolha_1 == 1)
            {

                System.out.println("\nO que deseja fazer?\n\n1) Jogar partida\n2) Sair");
                escolha_2 = scanner.nextInt();

                if(escolha_2 == 1)
                {

                    do
                    {

                        System.out.print("\nJogadores na mesa: " + Partida.getJogadores().size() + " de " + Partida.getLimiteJogadores() + "\n\n");

                        for(int i = 0; i < Partida.getJogadores().size(); i++)
                        {

                            nome = Partida.getJogadorPorId(i + 1);

                            System.out.println("J" + (i + 1) + ": " + nome);

                        }

                        switch(escolha_2)
                        {

                            case 1:

                                System.out.println("\nCrie sua partida:\n\n1) Começar partida\n2) Adicionar Jogador\n3) Adicionar Bot\n4) Remover Participante\n5) Sair da Partida");

                                escolha_3 = scanner.nextInt();

                                switch(escolha_3)
                                {

                                    case 1:

                                        if(Partida.getJogadores().isEmpty() || Partida.getJogadores().size() < 2 && Partida.getJogadores().contains(Partida.possuiJogadorHumano()))
                                        {

                                            System.out.println("\nQuantidade de jogadores insuficiente");

                                        }

                                        else
                                        {

                                            Jogo rodadaAtual = new Jogo(scanner);

                                            rodadaAtual.continuarJogo();

                                            Partida.reiniciarPartida();

                                        }

                                        break;

                                    case 2:

                                        scanner.nextLine(); 

                                        System.out.println("\nQual o seu nome?");
                                        nome = scanner.nextLine();

                                        try
                                        {
                                            
                                            Jogador novoJogador = new Jogador(nome);
                                            Partida.adicionarJogador(novoJogador);

                                        }

                                        catch(Exception error)
                                        {

                                            System.out.println("\nErro: " + error.getMessage() + "\n");

                                        }

                                        break;

                                    case 3:

                                        System.out.println("\nQual o nível de dificuldade do bot?(1 a 3)");
                                        escolha_4 = scanner.nextInt();

                                        try
                                        {
                                        
                                            Partida.adicionarBot(escolha_4);

                                        }
                                        catch(Exception error)
                                        {

                                            System.out.println("\nErro: " + error + "\n");

                                        }

                                        break;

                                    case 4:

                                        System.out.println("\nQual jogador você deseja remover?");
                                        escolha_4 = scanner.nextInt();

                                        try
                                        {

                                            Partida.getRemoverJogador(escolha_4);

                                        }
                                        catch(Exception error)
                                        {

                                            System.out.println("\nErro: " + error + "\n");

                                        }

                                        break;


                                    case 5:

                                        break;

                                    default:

                                        System.out.println("\nInsira um valor válido para isto");

                                }

                                break;

                            case 2:

                                break;

                            default:

                                System.out.println("\n| Resposta inválida! |\n");

                                break;
                                
                        }
                        
                    } while(escolha_2 != 2);
                }
                
            }

            else
            {

                System.out.println("\nAté uma próxima!");

            }

        } while (escolha_1 != 2);

        scanner.close();

    }

}
