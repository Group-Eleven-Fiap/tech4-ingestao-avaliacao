package ingestao_avaliacao.domain;

public class AvaliacaoRequest {

    private String descricao;
    private Integer nota;

    // Getters e Setters
    public String getDescricao() { return descricao; }
    public void setDescricao(String descricao) { this.descricao = descricao; }

    public Integer getNota() { return nota; }
    public void setNota(Integer nota) { this.nota = nota; }
}
