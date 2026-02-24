package com.gourav.CodyWar.Repository;

import com.gourav.CodyWar.Domain.Entity.TestCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TestCaseRepository extends JpaRepository<TestCase, UUID> {

    /**
     * Returns all test cases for a given problem, ordered by orderIndex ascending.
     * Used by CodeExecutionService to fetch all test cases for judging.
     */
    List<TestCase> findByProblemIdOrderByOrderIndexAsc(UUID problemId);

    /**
     * Returns only the non-hidden (sample) test cases for a problem.
     * Used when showing example test cases to users in the battle room.
     */
    List<TestCase> findByProblemIdAndIsHiddenFalseOrderByOrderIndexAsc(UUID problemId);

    /**
     * Returns non-hidden test cases for multiple problems in a single query.
     * Used to avoid N+1 query problem when fetching all problems with their test cases.
     * Results are ordered by problem ID and then by orderIndex.
     */
    @Query("SELECT tc FROM TestCase tc WHERE tc.problem.id IN :problemIds AND tc.isHidden = false ORDER BY tc.problem.id, tc.orderIndex")
    List<TestCase> findVisibleTestCasesByProblemIdIn(@Param("problemIds") List<UUID> problemIds);

    /**
     * Returns the total count of test cases for a given problem.
     * Used to determine totalTestCases in submission results.
     */
    long countByProblemId(UUID problemId);
}
