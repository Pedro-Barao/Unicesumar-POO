package br.com.poker.model;

public class Cartas {
    
    public static int obterValor(String carta)
    {

        String valor = carta.split(" ")[0].trim();

        if(valor.equals("J")) { return 11; }
        else if(valor.equals("Q")) { return 12; }
        else if(valor.equals("K")) { return 13; }
        else if(valor.equals("A")) { return 14; }
        else { return Integer.parseInt(valor); }

    }

    public static String obterNipe(String carta)
    {

        return carta.split(" ")[1].trim();
        
    }

}
