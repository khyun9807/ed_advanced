package com.stock_concurrency_project.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class Stock {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long productId;

    private Long quantity;

    //optimistic lock용 필드
    @Version
    private Long version;

    public void decrease(Long quantity) {
        if(this.quantity<quantity) {
            throw new RuntimeException("Not enough stock");
        }

        this.quantity -= quantity;
    }


    public static Stock create(Long productId, Long quantity) {
        return Stock.builder()
                .productId(productId)
                .quantity(quantity)
                .build();
    }
}
