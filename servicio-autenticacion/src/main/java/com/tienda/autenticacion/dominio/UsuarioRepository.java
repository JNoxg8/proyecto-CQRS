package com.tienda.autenticacion.dominio;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Data access for {@link Usuario}. Spring Data JPA generates the implementation from the method names. */
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmail(String email);

    boolean existsByEmail(String email);
}
