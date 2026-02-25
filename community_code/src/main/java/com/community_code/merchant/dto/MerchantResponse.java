package com.community_code.merchant.dto;

import com.community_code.merchant.entity.Merchant;

public class MerchantResponse {

    /**
     * Merchant Item
     */
    public record MerchantItem(
            Long id,
            String name,
            String imageUrl,
            Long categoryId,
            String categoryName
    ) {
        public static MerchantItem from(Merchant merchant) {
            return new MerchantItem(
                    merchant.getId(),
                    merchant.getName(),
                    merchant.getImageUrl(),
                    merchant.getCategory().getId(),
                    merchant.getCategory().getName()
            );
        }
    }
}