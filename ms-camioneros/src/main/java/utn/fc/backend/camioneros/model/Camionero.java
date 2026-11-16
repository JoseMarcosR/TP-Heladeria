package utn.fc.backend.camioneros.model;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "camionero", indexes = @Index(name = "idx_camionero_cola", columnList = "estado, ultima_actualizacion_cola"))
public class Camionero {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_camionero")
    private Long id;

    /** Relación estricta 1 a 1 chofer-vehículo. */
    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL, optional = false)
    @JoinColumn(name = "id_camion", nullable = false, unique = true)
    private Camion camion;

    @Column(name = "dni", nullable = false, unique = true, length = 20)
    private String dni;

    @Column(name = "nombre_completo", nullable = false, length = 150)
    private String nombreCompleto;

    @Column(name = "telefono", nullable = false, length = 30)
    private String telefono;

    @Column(name = "contrato_activo", nullable = false)
    private boolean contratoActivo;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoCamionero estado;

    /** Posición en la cola FIFO global: el más antiguo es el próximo en ser asignado. */
    @Column(name = "ultima_actualizacion_cola", nullable = false)
    private Instant ultimaActualizacionCola;

    protected Camionero() {
    }

    public Camionero(Camion camion, String dni, String nombreCompleto, String telefono, boolean contratoActivo) {
        this.camion = camion;
        this.dni = dni;
        this.nombreCompleto = nombreCompleto;
        this.telefono = telefono;
        this.contratoActivo = contratoActivo;
        this.estado = EstadoCamionero.DISPONIBLE;
        this.ultimaActualizacionCola = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Camion getCamion() {
        return camion;
    }

    public String getDni() {
        return dni;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public String getTelefono() {
        return telefono;
    }

    public boolean isContratoActivo() {
        return contratoActivo;
    }

    public EstadoCamionero getEstado() {
        return estado;
    }

    public void setEstado(EstadoCamionero estado) {
        this.estado = estado;
    }

    public Instant getUltimaActualizacionCola() {
        return ultimaActualizacionCola;
    }

    public void setUltimaActualizacionCola(Instant ultimaActualizacionCola) {
        this.ultimaActualizacionCola = ultimaActualizacionCola;
    }
}
