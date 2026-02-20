package com.gourav.CodyWar.Repository;

import com.gourav.CodyWar.Domain.Entity.Problem;
import com.gourav.CodyWar.Domain.Entity.Difficulty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProblemRepository extends JpaRepository<Problem, UUID> {

    List<Problem> findByDifficulty(Difficulty difficulty);

    List<Problem> findByTitleContainingIgnoreCase(String keyword);

    Page<Problem> findByDifficulty(Difficulty difficulty, Pageable pageable);

    boolean existsByTitle(String title);
}