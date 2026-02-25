package com.community_code.security;

import com.community_code.auth.AuthException;
import com.community_code.global.response.ErrorCode;
import com.community_code.user.repository.UserRepository;
import com.community_code.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String userIdentifier) throws UsernameNotFoundException {
        User activeUser = getActiveUserByUserIdentifier(userIdentifier);
        return new CustomUserDetails(activeUser);
    }

    public User getActiveUserByUserIdentifier(String userIdentifier) {
        User user = userRepository.findByUserIdentifier(userIdentifier)
                .orElseThrow(() -> new AuthException(ErrorCode.USER_NOT_FOUND_AUTH));

        if (!user.isActive()) {
            throw new AuthException(ErrorCode.USER_NOT_FOUND_AUTH);
        }
        return user;
    }
}
