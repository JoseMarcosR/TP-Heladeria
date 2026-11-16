package utn.fc.backend.pedidos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import utn.fc.backend.pedidos.model.Region;

public interface RegionRepository extends JpaRepository<Region, Long> {
}
