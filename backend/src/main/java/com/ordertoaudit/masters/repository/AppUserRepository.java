package com.ordertoaudit.masters.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ordertoaudit.masters.entity.AppUser;
import com.ordertoaudit.masters.entity.UserRole;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    List<AppUser> findByRoleAndActiveTrueOrderByFullNameAsc(UserRole role);
}
