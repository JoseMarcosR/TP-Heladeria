package utn.fc.backend.pedidos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "pedido", indexes = @Index(name = "idx_pedido_estado_fecha", columnList = "estado, fecha_creacion"))
public class Pedido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_pedido")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_heladeria", nullable = false)
    private Heladeria heladeria;

    @Column(name = "cantidad_tachos", nullable = false)
    private int cantidadTachos;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoPedido estado;

    @Column(name = "fecha_creacion", nullable = false)
    private Instant fechaCreacion;

    /**
     * Criterio de orden de la cola regional. Nace igual a la fecha de creación y solo cambia cuando ms-logistica
     * pide reencolar el pedido al final (heladería con coordenadas inválidas, RN-05). La fecha de creación nunca se
     * modifica. Es nullable para que ddl-auto=update pueda agregar la columna sobre filas ya existentes: si es null,
     * la cola usa fecha_creacion (ver PedidoRepository.findCola).
     */
    @Column(name = "ultima_actualizacion_cola")
    private Instant ultimaActualizacionCola;

    protected Pedido() {
    }

    /** Un pedido nuevo siempre nace PENDIENTE y se encola por fecha de creación (FIFO regional). */
    public Pedido(Heladeria heladeria, int cantidadTachos) {
        this.heladeria = heladeria;
        this.cantidadTachos = cantidadTachos;
        this.estado = EstadoPedido.PENDIENTE;
        this.fechaCreacion = Instant.now();
        this.ultimaActualizacionCola = this.fechaCreacion;
    }

    public Long getId() {
        return id;
    }

    public Heladeria getHeladeria() {
        return heladeria;
    }

    public int getCantidadTachos() {
        return cantidadTachos;
    }

    public EstadoPedido getEstado() {
        return estado;
    }

    public void setEstado(EstadoPedido estado) {
        this.estado = estado;
    }

    public Instant getFechaCreacion() {
        return fechaCreacion;
    }

    public Instant getUltimaActualizacionCola() {
        return ultimaActualizacionCola;
    }

    public void setUltimaActualizacionCola(Instant ultimaActualizacionCola) {
        this.ultimaActualizacionCola = ultimaActualizacionCola;
    }
}
