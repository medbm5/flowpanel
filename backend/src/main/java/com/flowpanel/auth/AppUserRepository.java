package com.flowpanel.auth;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByPersona(String persona);

    List<AppUser> findAllByOrderByIdAsc();
}
