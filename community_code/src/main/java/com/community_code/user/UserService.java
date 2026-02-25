package com.community_code.user;

import com.community_code.auth.AuthException;
import com.community_code.auth.util.OAuthUserInfo;
import com.community_code.global.response.ErrorCode;
import com.community_code.user.entity.User;
import com.community_code.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {
    private final UserRepository userRepository;

    public User save(final User user) {
        return userRepository.save(user);
    }

    private void validateDuplicate(final User user) {
        Optional<User> findUser = switch (user.getAuthType()) {
            case KAKAO -> userRepository.findByOauthIdAndActive(user.getOauthId(), true);
            default ->
                    throw new IllegalArgumentException("Unsupported AuthType for duplicate validation: " + user.getAuthType());
        };

        if (findUser.isPresent()) {
            throw new AuthException(ErrorCode.USER_DUPLICATE);
        }
    }

    public Optional<User> getActiveOAuthUser(OAuthUserInfo oAuthUserInfo) {
        Optional<User> user =
                userRepository.findByOauthIdAndAuthType(
                        oAuthUserInfo.getOauthId(),
                        oAuthUserInfo.getAuthType()
                );

        user.ifPresent(
                u -> {
                    if (!u.isActive()) {
                        throw new AuthException(ErrorCode.USER_INACTIVE);
                    }
                }
        );

        return user;
    }

    public void delete(final User user) {
        userRepository.updateActiveById(false, user.getId());
    }
}
