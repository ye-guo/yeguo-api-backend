package icu.yeguo.yeguoapi.controller;


import cn.hutool.core.bean.BeanUtil;
import icu.yeguo.yeguoapi.common.ResponseCode;
import icu.yeguo.yeguoapi.common.Result;
import icu.yeguo.yeguoapi.common.ResultUtils;
import icu.yeguo.yeguoapi.exception.BusinessException;
import icu.yeguo.yeguoapi.model.dto.interfaceInfo.InterfaceInfoQueryRequest;
import icu.yeguo.yeguoapi.model.dto.interfaceInfo.InterfaceInfoRegisterRequest;
import icu.yeguo.yeguoapi.model.dto.interfaceInfo.InterfaceInfoUpdateRequest;
import icu.yeguo.yeguoapi.model.vo.InterfaceInfoVO;
import icu.yeguo.yeguoapi.service.InterfaceInfoService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;

import static icu.yeguo.yeguoapi.utils.IsAdminUtil.isAdmin;

@Slf4j
@RestController
@RequestMapping("/interfaceInfo")
public class InterfaceInfoController {

    @Autowired
    InterfaceInfoService interfaceInfoServiceImpl;

    /*
     *  注册
     * */
    @PostMapping("register")
    public Result<Long> interfaceInfoRegister(@RequestBody InterfaceInfoRegisterRequest interfaceInfoRegisterRequest, HttpServletRequest req) {
        if (interfaceInfoRegisterRequest == null) {
            throw new BusinessException(ResponseCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = interfaceInfoServiceImpl.interfaceInfoRegister(interfaceInfoRegisterRequest, req);
        return ResultUtils.success(id);
    }

    /*
     *  删除
     * */
    @DeleteMapping("{id}")
    public Result<Integer> removeById(@PathVariable Long id, HttpServletRequest req) {
        if (!isAdmin(req)) {
            throw new BusinessException(ResponseCode.NO_AUTH_ERROR, "普通用户,无权限执行此操作");
        }
        // 删除成功返回值为 1
        int result = interfaceInfoServiceImpl.rmByid(id);
        return ResultUtils.success(result);
    }

    /*
     *  修改
     * */
    @PutMapping("/update")
    public Result<Integer> updateById(@RequestBody InterfaceInfoUpdateRequest interfaceInfoUpdateRequest,
                                      HttpServletRequest req) {
        if (!isAdmin(req)) {
            throw new BusinessException(ResponseCode.NO_AUTH_ERROR, "普通用户,无权限执行此操作");
        }
        // 更新成功返回值为 1
        int result = interfaceInfoServiceImpl.upById(interfaceInfoUpdateRequest, req);
        return ResultUtils.success(result);
    }

    /*
     *  查询
     * */
    @GetMapping("dynamicQuery")
    public Result<ArrayList<InterfaceInfoVO>> dynamicQuery(InterfaceInfoQueryRequest interfaceInfoQueryRequest) {
        ArrayList<InterfaceInfoVO> interfaceInfoVOList;

        if (BeanUtil.isEmpty(interfaceInfoQueryRequest)) {
            interfaceInfoVOList = interfaceInfoServiceImpl.selectAll();
        } else {
            interfaceInfoVOList = interfaceInfoServiceImpl.dynamicQuery(interfaceInfoQueryRequest);
        }
        return ResultUtils.success(interfaceInfoVOList);
    }


}