package com.community_code.auth.util;

public interface OAuthUtil {
    OAuthUserInfo getUserInfoFromOAuthToken(String oAuthToken);
}
