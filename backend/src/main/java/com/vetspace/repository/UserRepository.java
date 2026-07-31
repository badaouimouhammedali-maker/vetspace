package com.vetspace.repository;

import com.vetspace.domain.user.Role;
import com.vetspace.domain.user.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    /**
     * Students per école for the admin overview, biggest first. Left join so a school
     * with no students still appears — a zero is a fact worth seeing on that screen.
     */
    @Query("""
        select new com.vetspace.admin.dto.AdminDtos$SchoolBreakdownDto(
            s.id, s.name, count(u.id))
        from School s left join User u on u.school = s and u.role = :role
        group by s.id, s.name
        order by count(u.id) desc, s.name asc
        """)
    List<com.vetspace.admin.dto.AdminDtos.SchoolBreakdownDto> countStudentsBySchool(@Param("role") Role role);

    /** Students whose école was never set — invisible to the join above. */
    long countByRoleAndSchoolIsNull(Role role);


    Optional<User> findByEmailIgnoreCase(String email);

    Optional<User> findByUsername(String username);

    long countByRole(Role role);

    List<User> findTop8ByRoleOrderByCreatedAtDesc(Role role);

    /** Admin "Abonnés" search: students only, blank query matches all, case-insensitive over identity fields. */
    @Query("""
        select u from User u
        where u.role = com.vetspace.domain.user.Role.STUDENT
          and (:q is null or :q = ''
               or lower(u.email) like lower(concat('%', :q, '%'))
               or lower(u.username) like lower(concat('%', :q, '%'))
               or lower(u.firstName) like lower(concat('%', :q, '%'))
               or lower(u.lastName) like lower(concat('%', :q, '%')))
        """)
    Page<User> searchStudents(@Param("q") String q, Pageable pageable);
}
