package com.lacaleta.repository;

import com.lacaleta.model.RecetaInsumo;
import org.springframework.data.jpa.repository.*;
import java.util.*;

public interface RecetaInsumoRepository extends JpaRepository<RecetaInsumo, Integer> {
    @EntityGraph(attributePaths = {"producto", "insumo"})
    List<RecetaInsumo> findByProductoIdIn(Collection<Integer> productos);

    @Modifying
    @Query("delete from RecetaInsumo r where r.producto.id = :productoId")
    int deleteByProductoId(@org.springframework.data.repository.query.Param("productoId") Integer productoId);
}
