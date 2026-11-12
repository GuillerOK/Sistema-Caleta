package com.lacaleta.repository;

import com.lacaleta.model.Combo;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ComboRepository extends JpaRepository<Combo, Integer> {
    List<Combo> findByActivoTrue();
    Optional<Combo> findByNombreIgnoreCase(String nombre);
}
