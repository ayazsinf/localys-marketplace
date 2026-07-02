package com.localys.marketplace.service;

import com.localys.marketplace.model.UserEntity;
import com.localys.marketplace.model.enums.USER_ROLE;
import com.localys.marketplace.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AdminRecipientService {

    private final UserRepository userRepository;

    public AdminRecipientService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public List<UserEntity> findAdmins() {
        return userRepository.findByRoleAndEnabledTrue(USER_ROLE.ROLE_ADMIN);
    }
}
