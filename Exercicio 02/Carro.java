public class Carro
  {
    private String modelo;
    private String cor;
    private int velocidade;
    
    public void buzinar()
    {
      System.out.println("O " + modelo + " " + cor + " fez: BIIII BIIII!");
    }

    public void setCor(String cor)
    {
      this.cor = cor;
    }

    public String getCor()
    {
      return cor;
    }

    public void setModelo(String modelo)
    {
      this.modelo = modelo;
    }

    public String getModelo()
    {
      return modelo;
    }
    
    public void setVelocidade(int velocidade)
    {
      if (velocidade < 0)
      {
        System.out.println("Erro: Velocidade não pode ser negativa");
      }
      else
      {
        this.velocidade = velocidade
      }
    }
    
    public int getVelocidade()
    {
      return velocidade;
    }
  }
