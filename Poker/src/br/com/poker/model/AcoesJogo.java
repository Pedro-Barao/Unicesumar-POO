package br.com.poker.model;

import br.com.poker.enums.TipoJogadas;

public class AcoesJogo {
    
    private TipoJogadas tipo;
    private int valor;

    public AcoesJogo(TipoJogadas tipo, int valor)
    {

        this.tipo = tipo;
        this.valor = valor;

    }

    public TipoJogadas getTipo()
    {

        return tipo;

    }

    public int getValor()
    {

        return valor;

    }
    
}
