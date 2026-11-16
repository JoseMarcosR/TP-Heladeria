package utn.fc.backend.pedidos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import utn.fc.backend.pedidos.model.Heladeria;

public interface HeladeriaRepository extends JpaRepository<Heladeria, Long> {
}
