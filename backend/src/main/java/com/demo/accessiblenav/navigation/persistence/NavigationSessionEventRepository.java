package com.demo.accessiblenav.navigation.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NavigationSessionEventRepository extends JpaRepository<NavigationSessionEventEntity, Long> {
}

