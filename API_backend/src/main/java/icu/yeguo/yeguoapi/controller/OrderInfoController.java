package icu.yeguo.yeguoapi.controller;

import cn.hutool.core.bean.BeanUtil;
import icu.yeguo.yeguoapi.common.ResponseCode;
import icu.yeguo.yeguoapi.common.Result;
import icu.yeguo.yeguoapi.common.ResultUtils;
import icu.yeguo.yeguoapi.exception.BusinessException;
import icu.yeguo.yeguoapi.model.dto.orderInfo.CreateOrderInfoRequest;
import icu.yeguo.yeguoapi.model.dto.orderInfo.OrderInfoQueryRequest;
import icu.yeguo.yeguoapi.model.entity.OrderInfo;
import icu.yeguo.yeguoapi.model.vo.OrderInfoVO;
import icu.yeguo.yeguoapi.service.OrderInfoService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import static icu.yeguo.yeguoapi.utils.IsAdminUtil.isAdmin;

@Slf4j
@RestController
@RequestMapping("/orderInfo")
public class OrderInfoController {
    @Autowired
    private OrderInfoService orderInfoServiceImpl;

    // 创建订单
    @PostMapping("")
    public Result<String> createOrderInfo(@RequestBody CreateOrderInfoRequest createOrderInfoRequest) {
        String orderInfoId = orderInfoServiceImpl.createOrderInfo(createOrderInfoRequest);
        return orderInfoId != null ? ResultUtils.success(orderInfoId) : ResultUtils.error("创建订单失败");
    }

    // 用户获取自己所有订单
    @GetMapping("{userId}/all")
    public Result<List<OrderInfoVO>> getUserAllOrderInfos(@PathVariable("userId") Long userId) {
        List<OrderInfoVO> list = orderInfoServiceImpl.getUserAllOrders(userId);
        return ResultUtils.success(list);
    }

    //  该方法需要覆盖上面的
    @GetMapping("dynamicQuery")
    public Result<List<OrderInfo>> dynamicQuery(OrderInfoQueryRequest orderInfoQueryRequest, HttpServletRequest req) {
        List<OrderInfo> orderInfoList;
        if (!isAdmin(req)) {
            throw new BusinessException(ResponseCode.NO_AUTH_ERROR, "普通用户，无权限执行此操作");
        }
        // hutool BeanUtil 属性都为空
        if (BeanUtil.isEmpty(orderInfoQueryRequest)) {
            orderInfoList = orderInfoServiceImpl.selectAll();
        } else {
            orderInfoList = orderInfoServiceImpl.dynamicQuery(orderInfoQueryRequest);
        }
        return ResultUtils.success(orderInfoList);
    }

    // 用户取消订单
    @PutMapping("cancel/{orderId}")
    public Result<Integer> cancelOrderInfo(@PathVariable("orderId") String orderId) {
        Integer result = orderInfoServiceImpl.cancelOrderInfo(orderId);
        if (orderInfoServiceImpl.cancelOrderInfo(orderId) == 2) {
            return ResultUtils.success(result);
        }
        return result == 1 ?
                ResultUtils.success(result) :
                ResultUtils.error("取消订单失败");
    }

    // 删除订单
    @DeleteMapping("{orderId}")
    public Result<Integer> deleteOrderInfo(@PathVariable("orderId") String orderId) {
        Integer result = orderInfoServiceImpl.deleteOrderInfo(orderId);
        return result == 1 ?
                ResultUtils.success(result) :
                ResultUtils.error("删除订单失败");
    }
}
