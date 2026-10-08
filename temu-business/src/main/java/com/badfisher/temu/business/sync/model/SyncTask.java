package com.badfisher.temu.business.sync.model;

import com.badfisher.temu.business.order.enums.TemuOrderStatus;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * Temu 数据拉取任务表 — 记录每次拉取的时间窗口和执行状态，通用支持订单/售后/库存等业务类型
 * <p>
 * 由对应的 BuildJob 为每个店铺创建任务（时间窗口）， 再由对应的 PullJob 消费并调用 Temu API 拉取数据。
 * <p>
 * state 状态流转：0(未请求) → 1(请求中) → 2(完成) / 3(失败)
 * <p>
 * pullType 区分业务类型：ORDER=订单, AFTERSALES=售后, STOCK=库存
 * <p>
 * pullStatus 业务状态过滤，与 pullType 联动使用：
 * <ul>
 * <li>ORDER: 对应 parentOrderStatus — 0=全部, 1=PENDING, 2=UN_SHIPPING, 3=CANCELED,
 * 4=SHIPPED, 5=RECEIPTED, 41=部分发货, 51=部分签收</li>
 * <li>AFTERSALES: 对应 aftersalesStatus — 0=全部, 其他值待确认</li>
 * <li>STOCK: 不使用，默认0</li>
 * </ul>
 *
 * @author lenkor
 */
@Data
@TableName("tb_temu_order_request_task")
public class SyncTask implements Serializable {

	private static final long serialVersionUID = 1L;

	@TableId(type = IdType.AUTO)
	private Long id;

	/** Temu App ID，参与请求授权唯一定位。 */
	private String appId;

	/** Temu授权/API路由区域(US/GLOBAL/EU/CN)。 */
	private String authRegion;

	/** 站点(US/AU/CN) */
	private String site;

	/** ERP中台渠道编号，唯一对应一个ERP店铺 */
	private Integer channelId;

	/** 店铺编码/卖家名称 */
	private String accountCode;

	/** 拉取类型: ORDER=订单, AFTERSALES=售后, STOCK=库存 */
	private String pullType;

	/** 拉取状态过滤: 与pullType联动, 0=全部(默认), 其他值参见 TemuOrderStatus 枚举 */
	private String pullStatus;

	/** 时间类型(create_time/lastmodifieddate) */
	private String timeType;

	/** 拉取开始时间 */
	private Date beginTime;

	/** 拉取结束时间 */
	private Date endTime;

	/** 任务状态: 0=未请求, 1=请求中, 2=完成, 3=失败 */
	private Integer state;

	/** 每次领取生成的新执行身份，所有执行回写均需匹配。 */
	private String executionId;

	/** 数据库时钟维护的租约截止时间。 */
	private Date leaseExpiresAt;

	/** 错误信息(state=3时) */
	private String errorMessage;

	@TableLogic(value = "0", delval = "1")
	private Integer delFlag;

	@TableField(fill = FieldFill.INSERT)
	private Date createTime;

	@TableField(fill = FieldFill.INSERT_UPDATE)
	private Date updateTime;

}
