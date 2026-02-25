package com.community_code.auth.util;

import com.community_code.user.entity.AuthType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class OAuthUserInfo {
    private String name;

    private String oauthId;

    private AuthType authType;
}
