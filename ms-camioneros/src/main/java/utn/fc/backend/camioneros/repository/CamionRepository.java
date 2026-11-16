package utn.fc.backend.camioneros.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import utn.fc.backend.camioneros.model.Camion;

public interface CamionRepository extends JpaRepository<Camion, Long> {

    boolean existsByPatente(String patente);
}
