package utn.fc.backend.pedidos.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import utn.fc.backend.pedidos.model.EstadoPedido;
import utn.fc.backend.pedidos.model.Pedido;

import java.util.List;
import java.util.Optional;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    /**
     * Cola regional FIFO: pedidos en un estado dado de una región, del más antiguo al más nuevo según
     * ultimaActualizacionCola (igual a la fecha de creación salvo que el pedido se haya reencolado al final).
     * Las filas anteriores a esa columna (null) se ordenan por fecha de creación, como antes.
     */
    @Query("""
            select p from Pedido p
              join fetch p.heladeria h
              join fetch h.ciudad c
              join fetch c.region r
            where r.id = :idRegion and p.estado = :estado
            order by coalesce(p.ultimaActualizacionCola, p.fechaCreacion) asc, p.id asc
            """)
    List<Pedido> findCola(@Param("idRegion") Long idRegion, @Param("estado") EstadoPedido estado);

    @Query("""
            select p from Pedido p
              join fetch p.heladeria h
              join fetch h.ciudad c
              join fetch c.region r
            where p.id = :id
            """)
    Optional<Pedido> findDetalleById(@Param("id") Long id);
}
