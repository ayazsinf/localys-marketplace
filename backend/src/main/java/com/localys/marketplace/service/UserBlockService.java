package com.localys.marketplace.service;

import com.localys.marketplace.dto.BlockedUserDto;
import com.localys.marketplace.model.UserBlock;
import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.repository.UserBlockRepository;
import com.localys.marketplace.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Blocking works both ways: once either user blocked the other, neither can message the other
 * or make them an offer. Only the blocker can lift it.
 */
@Service
public class UserBlockService {

    private final UserBlockRepository userBlockRepository;
    private final UserRepository userRepository;

    public UserBlockService(UserBlockRepository userBlockRepository, UserRepository userRepository) {
        this.userBlockRepository = userBlockRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public void block(Long blockerId, Long blockedId) {
        if (blockerId.equals(blockedId)) {
            throw new IllegalArgumentException("Cannot block yourself");
        }
        if (userBlockRepository.existsByBlocker_IdAndBlocked_Id(blockerId, blockedId)) {
            return;
        }
        UserEntity blocker = userRepository.findById(blockerId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        UserEntity blocked = userRepository.findById(blockedId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        UserBlock block = new UserBlock();
        block.setBlocker(blocker);
        block.setBlocked(blocked);
        userBlockRepository.save(block);
    }

    @Transactional
    public void unblock(Long blockerId, Long blockedId) {
        userBlockRepository.findByBlocker_IdAndBlocked_Id(blockerId, blockedId)
                .ifPresent(userBlockRepository::delete);
    }

    @Transactional(readOnly = true)
    public List<BlockedUserDto> list(Long blockerId) {
        return userBlockRepository.findByBlocker_IdOrderByCreatedAtDesc(blockerId).stream()
                .map(block -> new BlockedUserDto(
                        block.getBlocked().getId(),
                        block.getBlocked().getDisplayName(),
                        block.getCreatedAt()))
                .toList();
    }

    public boolean hasBlocked(Long blockerId, Long blockedId) {
        return blockerId != null && blockedId != null
                && userBlockRepository.existsByBlocker_IdAndBlocked_Id(blockerId, blockedId);
    }

    public boolean isBlockedBetween(Long a, Long b) {
        return a != null && b != null && userBlockRepository.existsBetween(a, b);
    }

    /** Throws when either user blocked the other. */
    public void ensureNotBlocked(Long a, Long b) {
        if (isBlockedBetween(a, b)) {
            throw new IllegalArgumentException("User is blocked");
        }
    }
}
