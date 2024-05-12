package com.yeguo.yeguoapi.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class InterfaceInfoVO implements Serializable {

    private static final long serialVersionUID = 3096120745422719654L;
    private Long id;
    private Long userId;
    private String name;
    private String description;
    private String method;
    private String url;
    private String requestParams;
    private String requestHeader;
    private String responseHeader;
    private String responseFormat;
    private String requestExample;
    private Integer interfaceStatus;
    private Long invokingCount;
    private String avatarUrl;
    private Long requiredGoldCoins;
    private Date createTime;
}
