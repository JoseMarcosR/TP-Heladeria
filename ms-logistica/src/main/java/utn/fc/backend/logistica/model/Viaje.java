package utn.fc.backend.logistica.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Hoja de ruta. id_region e id_camionero son referencias lógicas a otros servicios (sin FK).
 */
@Entity
@Table(name = "viaje")
public class Viaje {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_viaje")
    private Long id;

    @Column(name = "id_region", nullable = false)
    private Long idRegion;

    @Column(name = "id_camionero", nullable = false)
    private Long idCamionero;

    @Column(name = "total_tachos_cargados", nullable = false)
    private int totalTachosCargados;

    @Column(name = "tiempo_estimado_seg", nullable = false)
    private int tiempoEstimadoSeg;

    @Column(name = "url_google_maps", columnDefinition = "text")
    private String urlGoogleMaps;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoViaje estado;

    @Column(name = "fecha_planificacion", nullable = false)
    private Instant fechaPlanificacion;

    @Column(name = "fecha_cierre")
    private Instant fechaCierre;

    @OneToMany(mappedBy = "viaje", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordenVisita asc")
    private List<ParadaViaje> paradas = new ArrayList<>();

    protected Viaje() {
    }

    public Viaje(Long idRegion, Long idCamionero, int totalTachosCargados, int tiempoEstimadoSeg, String urlGoogleMaps) {
        this.idRegion = idRegion;
        this.idCamionero = idCamionero;
        this.totalTachosCargados = totalTachosCargados;
        this.tiempoEstimadoSeg = tiempoEstimadoSeg;
        this.urlGoogleMaps = urlGoogleMaps;
        this.estado = EstadoViaje.PLANIFICADO;
        this.fechaPlanificacion = Instant.now();
    }

    public void agregarParada(ParadaViaje parada) {
        parada.setViaje(this);
        paradas.add(parada);
    }

    /** Cierre manual (FUN-04). */
    public void finalizar(Instant fecha) {
        this.estado = EstadoViaje.FINALIZADO;
        this.fechaCierre = fecha;
    }

    public Long getId() {
        return id;
    }

    public Long getIdRegion() {
        return idRegion;
    }

    public Long getIdCamionero() {
        return idCamionero;
    }

    public int getTotalTachosCargados() {
        return totalTachosCargados;
    }

    public int getTiempoEstimadoSeg() {
        return tiempoEstimadoSeg;
    }

    public String getUrlGoogleMaps() {
        return urlGoogleMaps;
    }

    public EstadoViaje getEstado() {
        return estado;
    }

    public Instant getFechaPlanificacion() {
        return fechaPlanificacion;
    }

    public Instant getFechaCierre() {
        return fechaCierre;
    }

    public List<ParadaViaje> getParadas() {
        return paradas;
    }
}
