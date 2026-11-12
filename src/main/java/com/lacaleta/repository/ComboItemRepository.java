package com.lacaleta.repository;
import com.lacaleta.model.ComboItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ComboItemRepository extends JpaRepository<ComboItem, Long> {
    List<ComboItem> findByComboProductoIdOrderByIdAsc(Integer comboProductoId);
    void deleteByComboProductoId(Integer comboProductoId);
    boolean existsByProductoId(Integer productoId);
}
