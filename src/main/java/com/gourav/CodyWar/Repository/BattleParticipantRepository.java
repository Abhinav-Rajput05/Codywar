package com.gourav.CodyWar.Repository;

import com.gourav.CodyWar.Domain.Entity.BattleParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BattleParticipantRepository extends JpaRepository<BattleParticipant, UUID> {

    @Query("SELECT bp FROM BattleParticipant bp WHERE bp.battle.id = :battleId AND bp.user.id = :userId")
    Optional<BattleParticipant> findByBattleIdAndUserId(@Param("battleId") UUID battleId, @Param("userId") UUID userId);

    @Query("SELECT bp FROM BattleParticipant bp WHERE bp.battle.id = :battleId")
    List<BattleParticipant> findByBattleId(@Param("battleId") UUID battleId);

    @Query("SELECT COUNT(bp) > 0 FROM BattleParticipant bp WHERE bp.battle.id = :battleId AND bp.user.id = :userId")
    boolean existsByBattleIdAndUserId(@Param("battleId") UUID battleId, @Param("userId") UUID userId);
}
