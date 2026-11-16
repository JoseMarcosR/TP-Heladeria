package utn.fc.backend.pedidos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import utn.fc.backend.pedidos.model.Ciudad;

public interface CiudadRepository extends JpaRepository<Ciudad, Long> {
}
