package br.com.poker.web;

import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/** Um navegador conectado a uma mesa. As mensagens ficam numa fila até o stream (SSE) enviá-las. */
public class Cliente {

    final String id = UUID.randomUUID().toString();
    final MesaOnline mesa;
    final int assento;
    final BlockingQueue<String> fila = new LinkedBlockingQueue<>();
    volatile boolean ativo = true;

    Cliente(MesaOnline mesa, int assento)
    {

        this.mesa = mesa;
        this.assento = assento;

    }

    void enviar(Object mensagem)
    {

        if (ativo)
        {

            fila.offer(Json.escrever(mensagem));

        }

    }

}
