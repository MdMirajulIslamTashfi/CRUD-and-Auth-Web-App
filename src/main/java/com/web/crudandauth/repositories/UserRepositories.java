package com.web.crudandauth.repositories;

import com.web.crudandauth.entities.User;
import com.web.crudandauth.enums.Roles;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepositories extends JpaRepository<User, String> {
    Optional<User> findByEmail(String email);
    long countByRole(Roles role);
    Page<User> findByRoleNot(Roles role, Pageable pageable);
    @Query("""
            SELECT u FROM User u
            WHERE u.role != :role
              AND (
                    LOWER(u.id)        LIKE LOWER(CONCAT('%', :q, '%'))
                 OR LOWER(u.firstName) LIKE LOWER(CONCAT('%', :q, '%'))
                 OR LOWER(u.lastName)  LIKE LOWER(CONCAT('%', :q, '%'))
                 OR LOWER(CONCAT(u.firstName, ' ', u.lastName)) LIKE LOWER(CONCAT('%', :q, '%'))
                 OR LOWER(u.email)     LIKE LOWER(CONCAT('%', :q, '%'))
              )
            """)
    Page<User> searchByRoleNot(@Param("role") Roles role,
                               @Param("q") String query,
                               Pageable pageable);

}