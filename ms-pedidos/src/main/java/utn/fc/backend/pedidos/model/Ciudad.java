package utn.fc.backend.pedidos.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "ciudad")
public class Ciudad {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_ciudad")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_region", nullable = false)
    private Region region;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    protected Ciudad() {
    }

    public Ciudad(Region region, String nombre) {
        this.region = region;
        this.nombre = nombre;
    }

    public Long getId() {
        return id;
    }

    public Region getRegion() {
        return region;
    }

    public String getNombre() {
        return nombre;
    }
}
