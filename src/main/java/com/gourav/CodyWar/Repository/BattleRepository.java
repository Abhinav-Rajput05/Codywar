package com.gourav.CodyWar.Repository;

import com.gourav.CodyWar.Domain.Entity.Battle;
import com.gourav.CodyWar.Domain.Entity.BattleStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BattleRepository extends JpaRepository<Battle, UUID> {

    Optional<Battle> findByRoomCode(String roomCode);

    boolean existsByRoomCode(String roomCode);

    @Query("SELECT b FROM Battle b WHERE b.status = :status AND b.isPrivate = false")
    List<Battle> findPublicBattlesByStatus(@Param("status") BattleStatus status);

    @Query("SELECT b FROM Battle b WHERE b.status IN :statuses")
    List<Battle> findByStatusIn(@Param("statuses") List<BattleStatus> statuses);

    @Query("SELECT b FROM Battle b JOIN b.participants p WHERE p.user.id = :userId AND b.status IN :statuses")
    List<Battle> findByUserIdAndStatusIn(@Param("userId") UUID userId, @Param("statuses") List<BattleStatus> statuses);

    @Query("SELECT b FROM Battle b JOIN b.participants p WHERE p.user.id = :userId ORDER BY b.createdAt DESC")
    List<Battle> findByUserIdOrderByCreatedAtDesc(@Param("userId") UUID userId);

    @Query("SELECT COUNT(b) FROM Battle b JOIN b.participants p WHERE p.user.id = :userId AND b.status = :status")
    long countByUserIdAndStatus(@Param("userId") UUID userId, @Param("status") BattleStatus status);

    @Query("SELECT b FROM Battle b WHERE b.status = :status AND b.isPrivate = false AND SIZE(b.participants) < b.maxParticipants ORDER BY b.createdAt ASC")
    List<Battle> findAvailablePublicBattles(@Param("status") BattleStatus status);
}
