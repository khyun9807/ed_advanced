package com.coupon_fcfs_project.api.service;

import com.coupon_fcfs_project.api.domain.Coupon;
import com.coupon_fcfs_project.api.producer.CouponCreateProducer;
import com.coupon_fcfs_project.api.repository.CouponRedisRepository;
import com.coupon_fcfs_project.api.repository.CouponRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApplyService {
    private final CouponRepository couponRepository;
    private final CouponRedisRepository couponRedisRepository;
    private final CouponCreateProducer couponCreateProducer;

    //동시에 요청이 들어오면 동시성 문제 생겨요
    //레이스 컨디션 발생 == coupon db(의 카운트)에 동시 접근
    //쓰레드 1이 새로운 쿠폰 카운트 값에 영향을 주기 전에
    //쓰레드 2가 쿠폰 카운트 값을 조회하기 때문
    @Transactional
    public void apply(Long userId){
        long count = couponRepository.count();

        if(count>100){
            return;
        }

        couponRepository.save(Coupon.create(userId));
    }

    //락을 걸면 어떨까?
    //쿠폰 갯수 조회부터 쿠폰 생성까지 락을 걸어야한다.
    //락을 거는 구간이 길어져서 성능 불이익
    //우리의 1차 목적은 쿠폰 갯수에 대한 정합성

    //redis의 incr 활용
    //빠른 명렁어 + redis는 싱글쓰레드로 동작되기에 레이스 컨디션 X
    //레디스가 싱글 쓰레드로 동작한다 
    //== 쓰레드1이 변경한 쿠폰 수 업데이트 끝날 때까지 쓰레드2는 레디스 작업 wait됨
    //=> 모든 쓰레드에서 언제나 레디스에서 최신 쿠폰 수를 가져갈 수 있다
    @Transactional
    public void applyV2(Long userId){
        //db의 로우 갯수가 아닌 레디스에 저장된 쿠폰 수를 조회
        Long count = couponRedisRepository.increment();

        if(count>100){
            return;
        }

        couponRepository.save(Coupon.create(userId));
    }

    //쿠폰 갯수 정합성은 해결됐지만
    //쿠폰 정보 저장하는데에 RDB에 많은 부하를 주게된다.
    //쿠폰 정보 전용 RDB가 아닌 다른데에서도 사용하는 RDB였다면
    //다른 곳에서도 느려졌을 것.
    //쿠폰 생성 100000개 요청되고 나서 주문 생성, 회원가입 요청이 있으면
    //주문 생성, 회원 가입 요청은 계속 뒤로 밀리겠쬬? 타임아웃이 없다면. 있다면 요청 실패.
    //aws와 ngrinder을 활용해서 실험해보자
    //ngrinder->client -> ELB|nginx -> coupon api X 10... <=> RDB 상황
    //결과 : RDB 마이 아파,, cpu 사용량 100에 근접 -> 서비스 오류

    //=> 카프카를 활용해보자! == 분산 이벤트 스트리밍 플랫폼
    //이벤트 스트리밍 : 소스->목적지, 이벤트를 실시간 스트리밍
    //producer -> topic -> consumer
    //topic 에는 쿠폰의 소유주가 될 userId를 주고 받을 것이다.
    //config를 통해 producer, consumer 설정이 필요하다.
    //비동기 과정이다 왜냐? 동기적인 것은 message를 전송하는 것 까지가 동기적인것
    //message를 받고 처리하는 consumer는 비동기적으로 작동한다. 즉 시간이 더 걸린다는 뜻.
    //테스트 케이스는 message를 다 전송하면 종료한다. 즉 consumer가 처리가 다 안끝났는데도 종료할 수 있다.
    //테스트 케이스에 Thread sleep 적용하자.
    //카프카를 사용하면 api 코드 내에서 직접 쿠폰을 생성하는 거에 비해서 처리량을 조절할 수 있다.
    //처리량을 조절할 수 있으면 DB의 부하를 줄일 수 있다.
    @Transactional
    public void applyV3(Long userId){
        //db의 로우 갯수가 아닌 레디스에 저장된 쿠폰 수를 조회
        Long count = couponRedisRepository.increment();

        if(count>100){
            return;
        }

        //직접 쿠폰을 만드는 로직 삭제
        //couponRepository.save(Coupon.create(userId));

        //만든 프로듀서를 통해 토픽에 유저아이디 전송
        couponCreateProducer.createMessage(userId);
    }

    //요구 사항 변경 1인당 쿠폰 발급 무제한 -> 1인당 쿠폰 1개로 제한
    //DB레벨에서는 userID와 쿠폰 이름에 unique제약을 걸어 생성이 하나만되게 하는 방법 -> 다른 쿠폰은 여러개 가질 수 있다면 복잡해짐
    //범위로 락을 잡고 쿠폰 발급 여부를 처음에 결정하는 방법
    // -> consumer에 발급 코드가 있고 시간차가 있다. 락이 해제됐는데도 아직 컨슈머가 처리하고 있다면 중복 발급됨 -> 안 좋은 방법
    // 그리고 컨슈머가 아닌 api 내부에서 쿠폰 발급을 한다쳐도 락 범위가 넓어서 성능에 안좋아
    //=>set 자료구조를 사용하자! -> redis set을 이용하자!
    //즉 레디스에는 쿠폰 수, 쿠폰 받은 사람들 목록이 저장되는 것
    @Transactional
    public void applyV4(Long userId){
        if(couponRedisRepository.addUser(userId)!=1)
            return;

        //db의 로우 갯수가 아닌 레디스에 저장된 쿠폰 수를 조회
        Long count = couponRedisRepository.increment();

        if(count>100){
            return;
        }

        //직접 쿠폰을 만드는 로직 삭제
        //couponRepository.save(Coupon.create(userId));

        //만든 프로듀서를 통해 토픽에 유저아이디 전송
        couponCreateProducer.createMessage(userId);
    }

    //컨슈머에서 에러가 발생하면?
    //디비에는 생성되지 않은 채, 레디스 상에서는 쿠폰 수가 올라가 있는 상황
    //해당 실습에서는 백업 데이터와 로그를 남기는 걸로
    //백업 데이터는 배치 프로그램을 통해서 처리
}
