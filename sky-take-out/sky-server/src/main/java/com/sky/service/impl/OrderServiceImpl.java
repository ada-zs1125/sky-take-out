package com.sky.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.sky.constant.MessageConstant;
import com.sky.context.BaseContext;
import com.sky.dto.OrdersPageQueryDTO;
import com.sky.dto.OrdersSubmitDTO;
import com.sky.entity.AddressBook;
import com.sky.entity.OrderDetail;
import com.sky.entity.Orders;
import com.sky.entity.ShoppingCart;
import com.sky.exception.AddressBookBusinessException;
import com.sky.exception.OrderBusinessException;
import com.sky.exception.ShoppingCartBusinessException;
import com.sky.mapper.AddressBookMapper;
import com.sky.mapper.OrderDetailMapper;
import com.sky.mapper.OrderMapper;
import com.sky.mapper.ShoppingCartMapper;
import com.sky.result.PageResult;
import com.sky.result.Result;
import com.sky.service.OrderService;
import com.sky.vo.OrderSubmitVO;
import com.sky.vo.OrderVO;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 订单
 */
@Service
@Slf4j
@Transactional //保证多表的一致性
public class OrderServiceImpl implements OrderService {
    @Autowired
    private OrderMapper orderMapper;
    @Autowired
    private OrderDetailMapper orderDetailMapper;

    //为了异常处理
    @Autowired
    private ShoppingCartMapper shoppingCartMapper;//操作地址簿
    @Autowired
    private AddressBookMapper addressBookMapper;//操作购物车

    @Override
    public OrderSubmitVO submitOrder(OrdersSubmitDTO ordersSubmitDTO) {


        //1.处理业务异常（收货地址为空、购物车为空）
        /**
         * 1.异常情况的处理（收货地址为空、购物车为空）
         *   绝大部分情况下不做这个判断处理问题也不大，因为如果是小程序提交过来的请求
         *   其实在小程序那端也做了判断（收货地址为空、购物车为空也是不能提交数据的），
         *   但是为了代码的健壮性建议在后端还是多次判断一下，因为用户如果并不是通过
         *   小程序提交的而是通过其它的一些方式 比如postman来提交这些请求，这个时候
         *   是没有任何校验的，此时后端在不校验那再处理的时候可能就会出现各种问题。
         */

        //1.1 通过前端传递过来的地址簿id查询数据库是否有收货地址，如果查不到则抛出异常。
        AddressBook addressBook = addressBookMapper.getById(ordersSubmitDTO.getAddressBookId());
        if (addressBook == null) {
            //抛出业务异常
            throw new AddressBookBusinessException(MessageConstant.ADDRESS_BOOK_IS_NULL);
        }
        //1.2 查询当前用户的购物车数据（购物车为空也不能正常下单）
        Long userId = BaseContext.getCurrentId();//获取当前用户的id
        ShoppingCart shoppingCart = new ShoppingCart();
        shoppingCart.setUserId(userId);

        List<ShoppingCart> shoppingCartList = shoppingCartMapper.list(shoppingCart);
        if (shoppingCartList == null || shoppingCartList.size() == 0) {
            //抛出业务异常
            throw new ShoppingCartBusinessException(MessageConstant.SHOPPING_CART_IS_NULL);
        }

        //2.向订单表插入1条数据（用户不管买多少个商品，只要它提交就是一个订单，对应一条订单数据）
        //构造订单数据
        Orders order = new Orders();
        //OrdersSubmitDTO已经封装好了一些数据，所以进行一个对象的拷贝
        BeanUtils.copyProperties(ordersSubmitDTO,order);
        //设置剩余的参数：
        //用户的手机号，dto并没有给我们传递过来，通过地址簿id查询出地址数据，在地址数据中就包含用户的名字和手机号
        //    在前面异常判断中已经查过了，所以在这个地方直接取就可以。
        order.setPhone(addressBook.getPhone());
        order.setAddress(addressBook.getDetail());
        order.setConsignee(addressBook.getConsignee());//收货人
        //要求是字符串类型，这个地方返回的是Long类型，所以需要进行转化
        order.setNumber(String.valueOf(System.currentTimeMillis()));//订单号，使用当前系统时间的时间戳生成
        order.setUserId(userId);//当前订单是属于哪个用户的
        order.setStatus(Orders.PENDING_PAYMENT);//订单状态：此时是待付款
        order.setPayStatus(Orders.UN_PAID);//支付状态，用户刚完成下单所以是未支付状态
        order.setOrderTime(LocalDateTime.now());//下单时间

        //这个sql需要返回插入的主键值，在后面插入订单明细，在订单明细实体类中会使用当前这个订单的id
        orderMapper.insert(order);

        //3.向订单明细表插入n条数据（可能是一条也可能是多条）
        //     具体需要插入多少条数据，是由购物车中的商品决定的，因为前面做需求分析的时候
        //     提到了我们真正下单购买这些商品其实是由购物车里面的这些数据决定的，所以订单明细
        //     里面的数据如何封装就应该看购物车中的数据。
        //  购物车中的数据在前面异常处理的时候已经查过了，直接遍历购物车数据
        List<OrderDetail> orderDetailList = new ArrayList<>();
        for (ShoppingCart cart : shoppingCartList) {
            //一条购物车数据对应就需要封装成一个订单明细对象
            OrderDetail orderDetail = new OrderDetail();
            //购物车实体类和订单明细实体类中的属性名相同，所以直接使用对象属性拷贝来封装。
            BeanUtils.copyProperties(cart, orderDetail);
            //设置当前订单明细关联的订单id，订单插入生成的主键值，动态sql封装到了order的id属性上。
            orderDetail.setOrderId(order.getId());

            //方式一：单条数据插入，遍历一次插入一次
            //方式二：批量插入，效率更高，所以这里把获得的订单明细数据给它放在list集合里面，然后一次性的批量插入。
            orderDetailList.add(orderDetail);
        }
        orderDetailMapper.insertBatch(orderDetailList);//批量插入

        //4.清理当前用户的购物车中的数据（用户下单成功后，用户的这些购物车中的数据就不需要了）
        shoppingCartMapper.deleteByUserId(userId);//前面购物车模块已实现

        //5.封装VO返回结果
        OrderSubmitVO orderSubmitVO = OrderSubmitVO.builder()
                .id(order.getId())
                .orderNumber(order.getNumber())
                .orderAmount(order.getAmount())
                .orderTime(order.getOrderTime())
                .build();

        return orderSubmitVO;
    }




    /**
     * 用户端订单分页查询
     *
     * @param pageNum
     * @param pageSize
     * @param status
     * @return
     */
    @Override
    public PageResult pageQuery4User(int pageNum, int pageSize, Integer status) {
        //需要在查询功能之前开启分页功能：当前页的页码   每页显示的条数
        PageHelper.startPage(pageNum, pageSize);

        //封装所需的请求参数为DTO对象
        OrdersPageQueryDTO ordersPageQueryDTO = new OrdersPageQueryDTO();
        ordersPageQueryDTO.setUserId(BaseContext.getCurrentId());
        ordersPageQueryDTO.setStatus(status);

        // 分页条件查询
        Page<Orders> page = orderMapper.pageQuery(ordersPageQueryDTO);

        //由接口可知需要封装为orderVO类型：订单菜品信息orderDishes，订单详情orderDetailList
        List<OrderVO> list = new ArrayList();

        // 查询出订单明细，并封装入OrderVO进行响应
        if (page != null && page.getTotal() > 0) { //有订单才有必要接着查询订单详情信息
            for (Orders orders : page) {
                Long orderId = orders.getId();// 订单id

                // 根据订单id,查询订单明细
                List<OrderDetail> orderDetails = orderDetailMapper.getByOrderId(orderId);

                OrderVO orderVO = new OrderVO();
                BeanUtils.copyProperties(orders, orderVO);
                orderVO.setOrderDetailList(orderDetails);

                list.add(orderVO);
            }
        }
        return new PageResult(page.getTotal(), list);
    }

    /**
     * 查询订单详情
     *
     * @param id
     * @return
     */
    public OrderVO details(Long id) {
        // 根据id查询订单
        Orders orders = orderMapper.getById(id);

        // 查询该订单对应的菜品/套餐明细
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(orders.getId());

        // 将该订单及其详情封装到OrderVO并返回
        OrderVO orderVO = new OrderVO();
        BeanUtils.copyProperties(orders, orderVO);
        orderVO.setOrderDetailList(orderDetailList);

        return orderVO;
    }

    /**
     * 用户取消订单
     *
     * @param id
     */
    public void userCancelById(Long id) throws Exception {
        // 根据id查询订单
        Orders ordersDB = orderMapper.getById(id);

        // 校验订单是否存在
        if (ordersDB == null) {
            throw new OrderBusinessException(MessageConstant.ORDER_NOT_FOUND);
        }

        //订单状态 1待付款 2待接单 3已接单 4派送中 5已完成 6已取消
        if (ordersDB.getStatus() > 2) {
            throw new OrderBusinessException(MessageConstant.ORDER_STATUS_ERROR);
        }


        //以上验证都通过后，此时订单处于待支付和待接单状态下
        Orders orders = new Orders();
        orders.setId(ordersDB.getId());

        // 订单处于待接单状态下取消，需要进行退款
//        if (ordersDB.getStatus().equals(Orders.TO_BE_CONFIRMED)) {
//            //调用微信支付退款接口
//            weChatPayUtil.refund(
//                    ordersDB.getNumber(), //商户订单号
//                    ordersDB.getNumber(), //商户退款单号
//                    new BigDecimal(0.01),//退款金额，单位 元
//                    new BigDecimal(0.01));//原订单金额
//
//            //支付状态修改为 退款
//            orders.setPayStatus(Orders.REFUND);
//        }

        // 更新订单状态、取消原因、取消时间
        orders.setStatus(Orders.CANCELLED);
        orders.setCancelReason("用户取消");
        orders.setCancelTime(LocalDateTime.now());
        //orderMapper.update(orders);
    }

    /**
     * 再来一单
     *
     * @param id
     */
    public void repetition(Long id) {
        // 查询当前用户id
        Long userId = BaseContext.getCurrentId();

        // 根据订单id查询当前订单详情
        List<OrderDetail> orderDetailList = orderDetailMapper.getByOrderId(id);

        // 将订单详情对象转换为购物车对象
        List<ShoppingCart> shoppingCartList = orderDetailList.stream().map(x -> {
            ShoppingCart shoppingCart = new ShoppingCart();

            // 将原订单详情里面的菜品信息重新复制到购物车对象中
            BeanUtils.copyProperties(x, shoppingCart, "id");
            shoppingCart.setUserId(userId);
            shoppingCart.setCreateTime(LocalDateTime.now());

            return shoppingCart;
        }).collect(Collectors.toList());

        // 将购物车对象批量添加到数据库
        shoppingCartMapper.insertBatch(shoppingCartList);
    }





}

