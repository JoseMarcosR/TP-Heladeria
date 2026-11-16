package utn.fc.backend.camioneros.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import utn.fc.backend.camioneros.model.Camionero;
import utn.fc.backend.camioneros.model.EstadoCamionero;

import java.util.List;
import java.util.Optional;

public interface CamioneroRepository extends JpaRepository<Camionero, Long> {

    boolean existsByDni(String dni);

    /** Cola FIFO global (RN-04): disponibles con contrato activo, el más antiguo primero. */
    @Query("""
            select c from Camionero c
              join fetch c.camion cam
            where c.estado = :estado and c.contratoActivo = true and cam.activo = true
            order by c.ultimaActualizacionCola asc, c.id asc
            """)
    List<Camionero> findCola(@Param("estado") EstadoCamionero estado, Pageable pageable);

    @Query("""
            select c from Camionero c
              join fetch c.camion
            where c.id = :id
            """)
    Optional<Camionero> findDetalleById(@Param("id") Long id);
}
