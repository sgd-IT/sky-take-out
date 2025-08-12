package com.sky.task;

import com.sky.entity.Orders;
import com.sky.mapper.OrderMapper;
import com.sky.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@Slf4j
public class OrderTask {

    @Autowired
    private OrderMapper orderMapper;

    /**
     * 处理超时订单
     */
    @Scheduled(cron = "0 * * * * ? ")//每分钟执行一次
    //@Scheduled(cron = "1/5 * * * * ?")//每分钟执行一次
    public void processTimeoutOrder() {
        log.info("处理超时订单,{}", LocalDateTime.now());

        LocalDateTime localDateTime = LocalDateTime.now().minusMinutes(15);//当前时间-15分钟
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTime(Orders.PENDING_PAYMENT, localDateTime);

        if (ordersList != null && ordersList.size() > 0) {
            ordersList.forEach(order -> {
                order.setStatus(Orders.CANCELLED);
                order.setCancelReason("订单超时，自动取消");
                order.setCancelTime(LocalDateTime.now());
                orderMapper.update(order);
            });
        }
    }

    /**
     * 一直处于派送中状态的订单
     */
    @Scheduled(cron = "0 0 1 * * ? ")//每天凌晨1点执行一次
    //@Scheduled(cron = "0/5 * * * * ? ")//每5秒执行一次
    public void processDeliveryOrder() {
        log.info("处理处于派送中的订单,{}", LocalDateTime.now());

        LocalDateTime localDateTime = LocalDateTime.now().minusHours(1);//当前时间-1小时
        List<Orders> ordersList = orderMapper.getByStatusAndOrderTime(Orders.DELIVERY_IN_PROGRESS, localDateTime);
        if (ordersList != null && ordersList.size() > 0) {
            ordersList.forEach(order -> {
                order.setStatus(Orders.COMPLETED);
                order.setDeliveryTime(LocalDateTime.now());
                orderMapper.update(order);
            });
        }
    }
}
