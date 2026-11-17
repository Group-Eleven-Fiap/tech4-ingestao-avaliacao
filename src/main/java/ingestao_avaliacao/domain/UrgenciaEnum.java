package ingestao_avaliacao.domain;

import ingestao_avaliacao.exception.AvaliacaoInvalidaException;

public enum UrgenciaEnum {

    CRITICA(0, 4),
    MEDIA(5, 6),
    BAIXA(7, 10);

    private final int notaMinima;
    private final int notaMaxima;

    UrgenciaEnum(int notaMinima, int notaMaxima) {
        this.notaMinima = notaMinima;
        this.notaMaxima = notaMaxima;
    }

    // Método utilitário para descobrir a urgência com base na nota informada
    public static UrgenciaEnum fromNota(int nota) {
        if (nota < 0 || nota > 10) {
            throw new AvaliacaoInvalidaException("A nota deve ser um valor inteiro entre 0 e 10.");
        }

        for (UrgenciaEnum urgencia : values()) {
            if (nota >= urgencia.notaMinima && nota <= urgencia.notaMaxima) {
                return urgencia;
            }
        }
        throw new AvaliacaoInvalidaException("Nota inválida para classificação de urgência.");
    }
}
