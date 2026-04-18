package com.payper.server.post.redis.outbox_to_redis;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

@Entity
@Table(
        name = "post_like_outbox",
        indexes = {
            @Index(name = "idx_like_outbox_status_created", columnList = "status, created_at"),
            @Index(name = "idx_like_outbox_status_locked", columnList = "status, locked_at")
        })
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class PostLikeOutbox {//implements Persistable<String> {
    @Id
    private String id;

    //@Transient
    //private boolean isNew = true;

    private long postId;
    private long userId;
    private int delta; // 좋아요니까 +1/-1/실패시0

    // NEW/PROCESSING/DONE <= 아웃박스를 워커가 다룰땐 processing. 워커가 이미 끝낸 아웃박스는 done
    // PostLikeOutBoxRepository에서 redis에 반영안된 new 아웃박스를 가져오기 위함
    private String status;
    private LocalDateTime lockedAt; // processing이 오래된 아웃박스가 있을 경우 다시 status를 new로 만들기 위해
    private String lockedBy;

    private LocalDateTime createdAt;
    private LocalDateTime processedAt;

    public static PostLikeOutbox newEvent(UUID id, long postId, long userId, int delta) {
        PostLikeOutbox o = new PostLikeOutbox();
        o.id = id.toString();
        o.postId = postId;
        o.userId = userId;
        o.delta = delta;
        o.status = "NEW";
        o.createdAt = LocalDateTime.now();
        return o;
    }

    /*@Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    private void markNotNew() {
        this.isNew = false;
    }*/

    // 해당 아웃박스 내용 redis로 반영 중
    public void markProcessing(String workerId) {
        this.status = "PROCESSING";
        this.lockedAt = LocalDateTime.now();
        this.lockedBy = workerId;
    }

    // 해당 아웃박스 내용 redis에 반영 완료
    public void markDone() {
        this.status = "DONE";
        this.processedAt = LocalDateTime.now();
    }
}
