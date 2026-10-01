package com.flowpanel.mission;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestTemplateRepository extends JpaRepository<RequestTemplate, String> {

    List<RequestTemplate> findAllByOrderByCodeAsc();
}
