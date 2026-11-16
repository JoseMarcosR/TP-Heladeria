package utn.fc.backend.logistica.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Parada de un viaje. id_pedido es una referencia lógica a ms-pedidos (sin FK entre servicios). */
@Entity
@Table(name = "parada_viaje")
public class ParadaViaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_parada")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_viaje", nullable = false)
    private Viaje viaje;

    @Column(name = "id_pedido", nullable = false)
    private Long idPedido;

    @Column(name = "orden_visita", nullable = false)
    private int ordenVisita;

    @Column(name = "tiempo_descarga_min", nullable = false)
    private int tiempoDescargaMin;

    @Column(name = "entregado_confirmado", nullable = false)
    private boolean entregadoConfirmado;

    protected ParadaViaje() {
    }

    public ParadaViaje(Long idPedido, int ordenVisita, int tiempoDescargaMin) {
        this.idPedido = idPedido;
        this.ordenVisita = ordenVisita;
        this.tiempoDescargaMin = tiempoDescargaMin;
        this.entregadoConfirmado = false;
    }

    void setViaje(Viaje viaje) {
        this.viaje = viaje;
    }

    public Long getId() {
        return id;
    }

    public Viaje getViaje() {
        return viaje;
    }

    public Long getIdPedido() {
        return idPedido;
    }

    public int getOrdenVisita() {
        return ordenVisita;
    }

    public int getTiempoDescargaMin() {
        return tiempoDescargaMin;
    }

    public boolean isEntregadoConfirmado() {
        return entregadoConfirmado;
    }

    public void setEntregadoConfirmado(boolean entregadoConfirmado) {
        this.entregadoConfirmado = entregadoConfirmado;
    }
}
