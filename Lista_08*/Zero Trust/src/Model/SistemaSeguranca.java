package Model;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SistemaSeguranca {
    
    private Veiculo[] vagasGaragem;

    private List<Funcionario> catracaPrincipal = new ArrayList<>();

    private Set<Credencial> cofreFisico = new HashSet<>();


    public SistemaSeguranca(int totalVagas)
    {

        this.vagasGaragem = new Veiculo[totalVagas];

    }


    public void estacionarVeiculo(Veiculo veiculo, int vaga)
    {


    }

    public void registrarCatraca(Funcionario funcionario)
    {


    }

    public void acessarCofre(Credencial credencial)
    {

        
    }

}
