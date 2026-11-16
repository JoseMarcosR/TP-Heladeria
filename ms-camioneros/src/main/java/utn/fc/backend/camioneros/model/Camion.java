package utn.fc.backend.camioneros.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "camion")
public class Camion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_camion")
    private Long id;

    @Column(name = "patente", nullable = false, unique = true, length = 15)
    private String patente;

    /** Capacidad máxima del freezer, medida en cantidad de tachos de 10 L (hasta 500). */
    @Column(name = "capacidad_max_tachos", nullable = false)
    private int capacidadMaxTachos;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    protected Camion() {
    }

    public Camion(String patente, int capacidadMaxTachos) {
        this.patente = patente;
        this.capacidadMaxTachos = capacidadMaxTachos;
        this.activo = true;
    }

    public Long getId() {
        return id;
    }

    public String getPatente() {
        return patente;
    }

    public int getCapacidadMaxTachos() {
        return capacidadMaxTachos;
    }

    public boolean isActivo() {
        return activo;
    }
}
