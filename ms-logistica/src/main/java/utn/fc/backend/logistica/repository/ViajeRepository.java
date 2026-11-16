package utn.fc.backend.logistica.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import utn.fc.backend.logistica.model.Viaje;

import java.util.List;
import java.util.Optional;

public interface ViajeRepository extends JpaRepository<Viaje, Long> {

    @Query("select v from Viaje v left join fetch v.paradas where v.id = :id")
    Optional<Viaje> findDetalleById(@Param("id") Long id);

    @EntityGraph(attributePaths = "paradas")
    List<Viaje> findAllByOrderByFechaPlanificacionDesc();
}
