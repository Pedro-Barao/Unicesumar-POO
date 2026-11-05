package br.com.poker.model;

import java.util.ArrayList;
import java.util.List;

public class Jogador{
    
    public static final int fichas_iniciais = 1000;
    private String nomeJogador = "";
    private int fichas = fichas_iniciais;
    private boolean maoAtual = true;
    private int apostaNaRodada = 0;
    private List<String> cartasJogador = new ArrayList<>();

    public Jogador(String nomeJogador)
    {

        this.nomeJogador = nomeJogador;

    }

    public String getNomeJogador()
    {

        return nomeJogador;

    }

    public int getFichas()
    {

        return fichas;

    }

    public void setFichas(int fichas)
    {

        this.fichas = fichas;

    }

    public List<String> getCartas()
    {

        return cartasJogador;

    }

    public boolean getMaoAtual() 
    { 
        
        return maoAtual; 
    
    }

    public void setMaoAtual(boolean estaNaMaoAtual) 
    { 
        
        this.maoAtual = estaNaMaoAtual; 
    
    }

    public int getApostaNaRodada() 
    { 
        
        return apostaNaRodada; 
    
    }

    public void setApostaNaRodada(int apostaNaRodada) 
    { 
        
        this.apostaNaRodada = apostaNaRodada; 

    }

}
