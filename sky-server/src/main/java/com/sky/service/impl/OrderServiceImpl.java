package com.sky.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersPaymentDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.*;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.*;
import com.sky.result.PageResult;
import com.sky.service.OrderService;
import com.sky.utils.WeChatPayUtil;
import com.sky.vo.OrderPaymentVO;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;
    @Autowired
    private AddressBookMapper addressBookMapper;
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WeChatPayUtil weChatPayUtil;

    /**
     * 用户下单
     *
     * @param ordersSubmitDTO
     * @return
     */
    @Override
    @Transactional
    public OrderSubmitVO submit(OrdersSubmitDTO ordersSubmitDTO) {
        //处理各种业务异常（地址簿不存在，用户不存在，购物车数据为空）
        //查询地址簿是否存在
        AddressBook addressBookMapperId = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBookMapperId == null) {
            //地址簿不存在
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        //查询购物车数据是否存在
        ShoppingCart cart = new ShoppingCart();
        cart.setUserId(BaseContext.getCurrentId());
        List<ShoppingCart> list = shoppingCartMapper.list(cart);
        if (list == null || list.size() == 0) {
            //购物车数据为空
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //向订单表插入一条数据
        Orders orders = new Orders();
        BeanUtils.copyProperties(ordersSubmitDTO, orders);
        orders.setAddress(addressBookMapperId.getDetail());
        orders.setOrderTime(LocalDateTime.now());
        orders.setPayStatus(Orders.UN_PAID);
        orders.setStatus(Orders.PENDING_PAYMENT);
        orders.setNumber(String.valueOf(System.currentTimeMillis()));
        orders.setPhone(addressBookMapperId.getPhone());
        orders.setConsignee(addressBookMapperId.getConsignee());
        orders.setUserId(BaseContext.getCurrentId());

        orderMapper.insert(orders);
        //向订单明细表插入多条数据
        List<OrderDetail> orderDetails = new ArrayList<>();

        for (ShoppingCart shoppingCart : list) {
            OrderDetail orderDetail = new OrderDetail();//订单明细
            BeanUtils.copyProperties(shoppingCart, orderDetail);
            orderDetail.setOrderId(orders.getId());//设置当前订单明细关联的订单id

            orderDetails.add(orderDetail);
        }
        //向订单明细表批量插入数据
        orderDetailMapper.insertBatch(orderDetails);
        //清空当前用户购物车的数据
        shoppingCartMapper.deleteByUserId(BaseContext.getCurrentId());
        //封装VO的返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(orders.getId())
                .orderNumber(orders.getNumber())
                .orderAmount(orders.getAmount())
                .orderTime(orders.getOrderTime())
                .build();
        return orderSubmitVO;
    }

    /**
     * 订单支付
     *
     * @param ordersPaymentDTO
     * @return
     */
    public OrderPaymentVO payment(OrdersPaymentDTO ordersPaymentDTO) throws Exception {
        // 当前登录用户id
        Long userId = BaseContext.getCurrentId();
        User user = userMapper.getById(userId);

//        //调用微信支付接口，生成预支付交易单
//        JSONObject jsonObject = weChatPayUtil.pay(
//                ordersPaymentDTO.getOrderNumber(), //商户订单号
//                new BigDecimal(0.01), //支付金额，单位 元
//                "苍穹外卖订单", //商品描述
//                user.getOpenid() //微信用户的openid
//        );
//
//        if (jsonObject.getString("code") != null && jsonObject.getString("code").equals("ORDERPAID")) {
//            throw new OrderBusinessException("该订单已支付");
//        }
        //模拟支付成功 直接跳过微信支付weChatPayUtil.pay的步骤，直接判断订单已支付
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("code", "ORDERPAID");

        OrderPaymentVO vo = jsonObject.toJavaObject(OrderPaymentVO.class);
        vo.setPackageStr(jsonObject.getString("package"));

        Orders orders = Orders.builder()
                .number(ordersPaymentDTO.getOrderNumber())
                .status(Orders.TO_BE_CONFIRMED)
                .payStatus(Orders.PAID)
                .checkoutTime(LocalDateTime.now())
                .build();

        orderMapper.update(orders);

        return vo;
    }

    /**
     * 查询订单详情
     *
     * @param id
     * @return
     */
    @Override
    public OrderVO queryOrderDetail(Long id) {
        //首先查询订单表信息
        Orders orders = orderMapper.getById(id);

        //根据订单id查询订单明细信息
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        //封装VO
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailList);
        orderVO.setAddress(orders.getAddress());

        return orderVO;
    }

    /**
     * 历史订单分页查询
     *
     * @param ordersPageQueryDTO 订单分页查询条件DTO，包含页码、每页大小、用户ID、订单状态等查询参数
     * @return PageResult 分页结果对象，包含总记录数和当前页的订单VO列表
     */
    @Override
    public PageResult pageHistoryOrders(OrdersPageQueryDTO ordersPageQueryDTO) {

        // 开启分页插件，设置分页参数
        PageHelper.startPage(ordersPageQueryDTO.getPage(), ordersPageQueryDTO.getPageSize());
        //分页需要根据全部/待付款/已取消
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(ordersPageQueryDTO.getStatus());
        //根据用户id和订单状态查询订单
        Page<Orders> page = orderMapper.pageHistoryOrders(ordersPageQueryDTO);

        // 构造订单VO列表，包含订单详情信息
        List<OrderVO> orderVOList = new ArrayList<>();

        if (page != null && page.getTotal() > 0) {
            for (Orders orders : page) {
                Long orderId = orders.getId();

                List<OrderDetail> list = orderDetailMapper.getByOrderId(orderId);
                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(list);
                orderVOList.add(orderVO);
            }
        }
        return new PageResult(page.getTotal(), orderVOList);
    }

    /**
     * 取消订单
     *
     * @param id
     */
    @Override
    public void cancelOrderById(Long id) {
        //判断订单是否存在
        Orders orders = orderMapper.getById(id);
        if (orders == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }
        //判断订单状态
        Integer status = orders.getStatus();
        if (status > 2) {
            throw new OrderBusinessException(MessageConstant.CANCEL_ORDER_CONTACT_BUSINESS);
        }


        Orders orders1 = new Orders();
        orders1.setId(orders.getId());

        if (orders.getStatus() == Orders.TO_BE_CONFIRMED) {
            orders1.setPayStatus(Orders.REFUND);
        }

        orders1.setStatus(Orders.CANCELLED);
        orders1.setCancelReason("用户取消了订单");
        orders1.setCancelTime(LocalDateTime.now());
        orderMapper.update(orders1);
    }

    /**
     * 再次下单
     *
     * @param id
     */
    @Override
    public void repetitionOrder(Long id) {
        //将原订单的商品重新加入到购物车

        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

        //stream API 转换为购物车对象
        List<ShoppingCart> shoppingCartList = orderDetailList.stream()
                .map(orderDetail -> ShoppingCart.builder()
                        .name(orderDetail.getName())
                        .image(orderDetail.getImage())
                        .userId(BaseContext.getCurrentId())
                        .dishId(orderDetail.getDishId())
                        .setmealId(orderDetail.getSetmealId())
                        .dishFlavor(orderDetail.getDishFlavor())
                        .number(orderDetail.getNumber())
                        .amount(orderDetail.getAmount())
                        .createTime(LocalDateTime.now())
                        .build())
                .collect(Collectors.toList());
        //批量插入购物车数据
        shoppingCartMapper.insertBatch(shoppingCartList);
    }

    /**
     * 支付成功，修改订单状态
     *
     * @param outTradeNo
     */
//    public void paySuccess(String outTradeNo) {
//
//        // 根据订单号查询订单
//        Orders ordersDB = orderMapper.getByNumber(outTradeNo);
//
//        // 根据订单id更新订单的状态、支付方式、支付状态、结账时间
//        Orders orders = Orders.builder()
//                .id(ordersDB.getId())
//                .status(Orders.TO_BE_CONFIRMED)
//                .payStatus(Orders.PAID)
//                .checkoutTime(LocalDateTime.now())
//                .build();
//
//        orderMapper.update(orders);
//    }
}
