package ingestao_avaliacao.service;

import ingestao_avaliacao.domain.AvaliacaoRequest;
import ingestao_avaliacao.domain.NotificacaoCritica;
import ingestao_avaliacao.exception.AvaliacaoInvalidaException;
import ingestao_avaliacao.exception.NotificacaoFalhouException;
import ingestao_avaliacao.handler.TestContext;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvaliacaoServiceTest {

    private static final Clock RELOGIO = Clock.fixed(Instant.parse("2026-11-08T14:32:01Z"), ZoneOffset.UTC);

    private final List<String[]> gravadas = new ArrayList<>();
    private final List<NotificacaoCritica> publicadas = new ArrayList<>();

    private AvaliacaoService service() {
        return new AvaliacaoService(
                (id, descricao, nota, urgencia, dataEnvio) -> gravadas.add(new String[]{id, descricao, String.valueOf(nota), urgencia, dataEnvio}),
                publicadas::add,
                RELOGIO);
    }

    private static AvaliacaoRequest avaliacao(String descricao, Integer nota) {
        AvaliacaoRequest request = new AvaliacaoRequest();
        request.setDescricao(descricao);
        request.setNota(nota);
        return request;
    }

    private String processar(AvaliacaoService service, String descricao, Integer nota) {
        return service.processarAvaliacao(avaliacao(descricao, nota), new TestContext().getLogger());
    }

    @Test
    void classificaAUrgenciaPelaNota() {
        AvaliacaoService service = service();
        int[] notas = {0, 4, 5, 6, 7, 10};
        String[] esperadas = {"CRITICA", "CRITICA", "MEDIA", "MEDIA", "BAIXA", "BAIXA"};

        for (int i = 0; i < notas.length; i++) {
            processar(service, "Aula " + notas[i], notas[i]);
            assertEquals(esperadas[i], gravadas.get(i)[3], "nota " + notas[i]);
        }
    }

    @Test
    void gravaTodosOsCamposComIdEDataGerados() {
        String id = processar(service(), "Aula sem áudio", 3);

        String[] item = gravadas.get(0);
        assertEquals(id, item[0]);
        assertEquals("Aula sem áudio", item[1]);
        assertEquals("3", item[2]);
        assertEquals("CRITICA", item[3]);
        assertEquals("2026-11-08T14:32:01", item[4]);
    }

    @Test
    void publicaNaFilaSomenteAvaliacoesCriticas() {
        AvaliacaoService service = service();

        processar(service, "Aula média", 5);
        processar(service, "Aula ótima", 9);
        assertTrue(publicadas.isEmpty());

        processar(service, "Aula sem áudio", 4);
        assertEquals(List.of(new NotificacaoCritica("Aula sem áudio", "CRITICA", "2026-11-08T14:32:01")), publicadas);
        assertEquals(3, gravadas.size());
    }

    @Test
    void recusaNotaForaDoIntervaloOuAusente() {
        AvaliacaoService service = service();

        assertThrows(AvaliacaoInvalidaException.class, () -> processar(service, "Aula", -1));
        assertThrows(AvaliacaoInvalidaException.class, () -> processar(service, "Aula", 11));
        assertThrows(AvaliacaoInvalidaException.class, () -> processar(service, "Aula", null));
        assertTrue(gravadas.isEmpty());
    }

    @Test
    void recusaDescricaoVaziaOuSoComEspacos() {
        AvaliacaoService service = service();

        assertThrows(AvaliacaoInvalidaException.class, () -> processar(service, null, 5));
        assertThrows(AvaliacaoInvalidaException.class, () -> processar(service, "   ", 5));
        assertTrue(gravadas.isEmpty());
    }

    @Test
    void recusaCorpoNulo() {
        assertThrows(AvaliacaoInvalidaException.class,
                () -> service().processarAvaliacao(null, new TestContext().getLogger()));
    }

    @Test
    void falhaNaFilaNaoEhEngolidaEInformaOIdDaAvaliacaoGravada() {
        AvaliacaoService service = new AvaliacaoService(
                (id, descricao, nota, urgencia, dataEnvio) -> gravadas.add(new String[]{id}),
                notificacao -> {
                    throw new IllegalStateException("SQS indisponível");
                },
                RELOGIO);

        NotificacaoFalhouException erro = assertThrows(NotificacaoFalhouException.class,
                () -> processar(service, "Aula sem áudio", 0));

        assertEquals(1, gravadas.size());
        assertEquals(gravadas.get(0)[0], erro.getIdAvaliacao());
    }
}
