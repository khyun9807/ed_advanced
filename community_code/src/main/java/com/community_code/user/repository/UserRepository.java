package com.community_code.user.repository;

import com.community_code.user.entity.AuthType;
import com.community_code.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUserIdentifier(String userIdentifier);

    @Query("update User u set u.active = :active where u.id = :id")
    @Modifying
    int updateActiveById(boolean active, Long id);

    Optional<User> findByOauthIdAndActive(String oauthId, boolean active);

    Optional<User> findByOauthIdAndAuthType(String oauthId, AuthType authType);
}
