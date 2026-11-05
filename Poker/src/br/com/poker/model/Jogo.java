package br.com.poker.model;

import java.util.Scanner;
import br.com.poker.enums.TipoJogadas;

public class Jogo {
    
    private final GerenciarJogo mesa;
    private final Scanner scanner;
    private int pote;
    private int apostaAtualMesa;

    public Jogo(Scanner scanner)
    {

        this.mesa = GerenciarJogo.getInstance();
        this.scanner = scanner;
        this.pote = 0;
        this.apostaAtualMesa = 0;

    }

    private void esperar(int milisegundos) 
    {

        try 
        {

            Thread.sleep(milisegundos);

        } 
            
        catch (InterruptedException e) 
        {

            Thread.currentThread().interrupt();

        }

    }

    private int contarJogadoresComFichas() 
    {

        int contador = 0;

        for (int i = 0; i < mesa.getJogadores().size(); i++) 
        {

            Jogador jogador = mesa.getJogadores().get(i);
        
            if(jogador.getFichas() > 0) 
            {

                contador++;

            }

        }

        return contador;

    }

    private int contarJogadoresAtivos() 
    {

        int ativos = 0;

        for (int i = 0; i < mesa.getJogadores().size(); i++) 
        {

            Jogador jogador = mesa.getJogadores().get(i);
        
            if(jogador.getMaoAtual()) 
            {

                ativos++;

            }

        }

        return ativos;

    }

    public void continuarJogo()
    {

        boolean continuarPartida = true;

        while(continuarPartida)
        {

            if(contarJogadoresComFichas() <= 1)
            {

                anunciarGrandeCampeaoFimDeJogo();

                return;

            }

            jogarMao();

            mesa.avancarDealer();

            boolean resposta = false;

            for (int i = 0; i < mesa.getJogadores().size(); i++) 
            {

                Jogador jogador = mesa.getJogadores().get(i);

                if(!(jogador instanceof Bot) && jogador.getFichas() > 0) 
                {

                    resposta = true;

                    System.out.println("\n" + jogador.getNomeJogador() + ", deseja continuar a partida? (1 - Sim | 2 - Terminar Torneio)");

                    int escolha = scanner.nextInt();

                    if(escolha == 2) 
                    {

                        continuarPartida = false;

                    }

                }

            }

            if(!resposta)
            {

                continuarPartida = false;

            }
            
        }

        anunciarVencedorPorPontosFinais();

    }

    public void jogarMao()
    {

        mesa.iniciarJogo();
        mesa.distribuirCartas();

        prepararRodadaAposta();

        cobrarBlindsObrigatorios();

        System.out.println("\n[ Rodada: Pré-Flop ]\n");
        rodadaDeApostas();

        if (contarJogadoresAtivos() == 1) 
        {

            encerrarMaoPorDesistência();

            return;

        }
        

        System.out.println("\n[ Abrindo o Flop... ]\n");
        esperar(1500);
        mesa.mostrarTodasCartasMesa(); 

        prepararRodadaAposta();

        rodadaDeApostas();

        if (contarJogadoresAtivos() == 1) 
        {

            encerrarMaoPorDesistência();

            return;

        }
        

        System.out.println("\n[ Abrindo o Turn... ]\n");
        esperar(1500);
        mesa.mostrarCartaMesa(); 

        prepararRodadaAposta();
        
        rodadaDeApostas();

        if (contarJogadoresAtivos() == 1) 
        {

            encerrarMaoPorDesistência();

            return;

        }

        System.out.println("\n[ Abrindo o River... ]\n");
        esperar(1500);
        mesa.mostrarCartaMesa(); 

        prepararRodadaAposta();
        
        rodadaDeApostas();

        if (contarJogadoresAtivos() == 1) 
        {

            encerrarMaoPorDesistência();

            return;

        }

        
        definirVencedorPorCartas();

    }

    private void prepararRodadaAposta()
    {

        apostaAtualMesa = 0;
        
        for(int i = 0; i < mesa.getJogadores().size(); i++) 
        {

            mesa.getJogadores().get(i).setApostaNaRodada(0);

        }

    }

    private void cobrarBlindsObrigatorios() 
    {

        int total = mesa.getJogadores().size();

        int Small_Blind_Index = (mesa.getDealerIndex() + 1) % total;
        int Big_Blind_Index = (mesa.getDealerIndex() + 2) % total;

        if (total == 2) 
        {

            Small_Blind_Index = mesa.getDealerIndex();
            Big_Blind_Index = (mesa.getDealerIndex() + 1) % total;

        }

        Jogador Small_Blind = mesa.getJogadores().get(Small_Blind_Index);
        Jogador Big_Blind = mesa.getJogadores().get(Big_Blind_Index);

        int cobrandoSmallBlind = Math.min(mesa.getSmallBlind(), Small_Blind.getFichas());
        Small_Blind.setApostaNaRodada(cobrandoSmallBlind);
        Small_Blind.setFichas(Small_Blind.getFichas() - cobrandoSmallBlind);
        
        int cobrandoBigBlind = Math.min(mesa.getBigBlind(), Big_Blind.getFichas());
        Big_Blind.setApostaNaRodada(cobrandoBigBlind);
        Big_Blind.setFichas(Big_Blind.getFichas() - cobrandoBigBlind);

        this.pote = cobrandoSmallBlind + cobrandoBigBlind;
        this.apostaAtualMesa = mesa.getBigBlind();

        System.out.println("-> " + Small_Blind.getNomeJogador() + " pagou Small Blind: " + cobrandoSmallBlind);
        System.out.println("-> " + Big_Blind.getNomeJogador() + " pagou Big Blind: " + cobrandoBigBlind);

    }

    public void rodadaDeApostas()
    {

        int total = mesa.getJogadores().size();
        int i = calcularInicioRodada();
        int pendentes = acaoPorPossiblidade(null);

        while(pendentes > 0)
        {

            boolean houveAumento = false;

            Jogador jogadorAtual = mesa.getJogadores().get(i);

            if(!jogadorAtual.getMaoAtual() || jogadorAtual.getFichas() <= 0)
            {

                i = (i + 1) % total;

                continue;

            }

            if(contarJogadoresAtivos() == 1)
            {

                return;

            }

            if(jogadorAtual instanceof Bot)
            {

                Bot bot = (Bot) jogadorAtual;

                System.out.print("-> " + bot.getNome() + " está pensando...");
                esperar(2000);
    
                System.out.print("\r");

                AcoesJogo acaoBot = bot.decisao(bot, mesa.getCartasMesa(), this.apostaAtualMesa, bot.getFichas(), this.pote);

                System.out.println("\n-> " + bot.getNome() + " fez: " + acaoBot.getTipo());

                if(acaoBot.getValor() > this.apostaAtualMesa)
                {

                    houveAumento = true;

                }

                if(acaoBot.getTipo() == TipoJogadas.RAISE)
                {

                    int custoReal = acaoBot.getValor() - bot.getApostaNaRodada();

                    this.apostaAtualMesa = acaoBot.getValor();

                    bot.setFichas(bot.getFichas() - custoReal);

                    this.pote += custoReal;

                    bot.setApostaNaRodada(acaoBot.getValor());

                }

                else if(acaoBot.getTipo() == TipoJogadas.CALL)
                {

                    int custoReal = this.apostaAtualMesa - bot.getApostaNaRodada();

                    bot.setFichas(bot.getFichas() - custoReal);

                    this.pote += custoReal;

                    bot.setApostaNaRodada(this.apostaAtualMesa);

                }

                else if (acaoBot.getTipo() == TipoJogadas.FOLD) 
                {

                    System.out.println("-> " + bot.getNome() + " desistiu da mão (FOLD).");
                    
                    jogadorAtual.setMaoAtual(false);
                    
                }

            }

            else
            {

                boolean jogadaValida = false;

                while(!jogadaValida)
                {

                    String posicaoBlind = mesa.identificarPosicaoBlind(i);

                    mesa.imprimirInterface(jogadorAtual, mesa.getCartasMesa(), this.pote, posicaoBlind);

                    int escolha = scanner.nextInt();

                    if(escolha == 1)
                    {

                        if(this.apostaAtualMesa > 0)
                        {

                            System.out.println("\nErro: Você não pode dar CHECK! Há uma aposta ativa.");

                        }

                        else
                        {

                            System.out.println("\nO Jogador(a) " + jogadorAtual.getNomeJogador() + " deu CHECK");

                            jogadaValida = true;

                        }

                    }

                    else if(escolha == 2) 
                    {

                        int custoReal = this.apostaAtualMesa - jogadorAtual.getApostaNaRodada();

                        System.out.println("\nO Jogador(a) " + jogadorAtual.getNomeJogador() + " deu CALL de " + custoReal + " fichas.");

                        jogadorAtual.setFichas(jogadorAtual.getFichas() - custoReal);

                        this.pote += custoReal;

                        jogadorAtual.setApostaNaRodada(this.apostaAtualMesa);

                        jogadaValida = true;

                    }

                    else if(escolha == 3) 
                    {

                        System.out.println("\nO Jogador(a) " + jogadorAtual.getNomeJogador() + " deu FOLD (Desistiu da mão).");

                        jogadorAtual.setMaoAtual(false);

                        jogadaValida = true;

                    }

                    else if(escolha == 4) 
                    {

                        System.out.print("Digite o valor total do seu aumento (RAISE): ");

                        int valorRaise = scanner.nextInt();
                        
                        if(valorRaise > jogadorAtual.getFichas()) 
                        {

                            System.out.println("\nErro: Você não tem fichas suficientes!");

                        } 
                        
                        else if(valorRaise <= this.apostaAtualMesa) 
                        {

                            System.out.println("\nErro: O aumento deve ser maior do que a aposta atual (" + this.apostaAtualMesa + ")!");

                        } 
                        
                        else 
                        {

                            int custoReal = valorRaise - jogadorAtual.getApostaNaRodada();

                            System.out.println("\nO Jogador(a) " + jogadorAtual.getNomeJogador() + " deu um RAISE para " + valorRaise + " fichas.");

                            jogadorAtual.setFichas(jogadorAtual.getFichas() - custoReal);

                            this.apostaAtualMesa = valorRaise;
                            this.pote += custoReal;

                            jogadorAtual.setApostaNaRodada(valorRaise);

                            jogadaValida = true;

                            houveAumento = true;

                        }

                    }

                }

            }

            if(houveAumento)
            {

                pendentes = acaoPorPossiblidade(jogadorAtual);

            }

            else
            {

                pendentes--;

            }

            i = (i + 1) % total;

        }

    }

    private int calcularInicioRodada()
    {

        int dealer = mesa.getDealerIndex();
        int total = mesa.getJogadores().size();

        if(!mesa.getCartasMesa().isEmpty())
        {

            return (dealer + 1) % total;

        }

        else if(total == 2)
        {

            return dealer;

        }

        else
        {

            return (dealer + 3) % total;

        }

    }

    private int acaoPorPossiblidade(Jogador jogadorIgnorado)
    {

        int contador = 0;

        for(int i = 0; i < mesa.getJogadores().size(); i++)
        {

            Jogador jogador = mesa.getJogadores().get(i);

            if(jogador != jogadorIgnorado && jogador.getMaoAtual() && jogador.getFichas() > 0)
            {

                contador++;

            }

        }

        return contador;

    }


    private void encerrarMaoPorDesistência() 
    {
    
        for(int i = 0; i < mesa.getJogadores().size(); i++) 
        {

            Jogador jogador = mesa.getJogadores().get(i);

            if (jogador.getMaoAtual()) 
            {

                jogador.setFichas(jogador.getFichas() + this.pote);

                esperar(1000);
                System.out.println("\n=======================================================");
                System.out.println("Todos os outros jogadores deram FOLD!");
                System.out.println("O jogador " + jogador.getNomeJogador() + " ganhou o pote de " + this.pote + " fichas!");
                System.out.println("=======================================================");
                esperar(2000);
                
                break;
                
            }

        }

    }

    private void anunciarGrandeCampeaoFimDeJogo() 
    {

        Jogador campeao = null;

        for (Jogador j : mesa.getJogadores()) 
        {

            if (j.getFichas() > 0) 
            {

                campeao = j;

            }

        }

        System.out.println("\n=======================================================");
        System.out.println("FIM DE JOGO! TODOS OS OUTROS FALIRAM!");
        if (campeao != null) 
        {

            System.out.println("O ÚNICO SOBREVIVENTE E GRANDE CAMPEÃO É: " + campeao.getNomeJogador() + "!!!");

        }

        System.out.println("=======================================================");
        esperar(4000);

    }

    private void definirVencedorPorCartas() 
    {
    
        for(int i = 0; i < mesa.getJogadores().size(); i++) 
        {

            Jogador jogador = mesa.getJogadores().get(i);

            if (jogador.getMaoAtual()) 
            {

                jogador.setFichas(jogador.getFichas() + this.pote);

                System.out.println("\n--- Mão finalizada no River! " + jogador.getNomeJogador() + " venceu e levou " + this.pote + " fichas. ---");

                break;

            }

            esperar(2000);

        }

    }

    private void anunciarVencedorPorPontosFinais() 
    {

        Jogador lider = null;

        for (int i = 0; i < mesa.getJogadores().size(); i++) 
        {

            Jogador jogador = mesa.getJogadores().get(i);

            if (lider == null || jogador.getFichas() > lider.getFichas()) 
            {

                lider = jogador;

            }

        }

        System.out.println("\n=======================================================");
        System.out.println("              PARTIDA ENCERRADA POR VOTAÇÃO!             ");
        
        if (lider != null) 
        {

            System.out.println("  O grande campeão por pontos foi: " + lider.getNomeJogador());
            System.out.println("  Ele acumulou um total de: " + lider.getFichas() + " fichas!");

        }
        
        System.out.println("=======================================================");
        esperar(4000);

    }
  
}