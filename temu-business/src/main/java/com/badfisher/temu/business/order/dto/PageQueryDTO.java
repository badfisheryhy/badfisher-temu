package com.badfisher.temu.business.order.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

/**
 * 分页查询基础参数
 *
 * @author lenkor
 */
@Data
@ApiModel("分页查询参数")
public class PageQueryDTO implements Serializable {

	private static final long serialVersionUID = 1L;

	@ApiModelProperty(value = "页码，从1开始", example = "1")
	private Integer pageNum = 1;

	@ApiModelProperty(value = "每页条数，最大200", example = "20")
	private Integer pageSize = 20;

	/** 返回可安全用于数据库分页的页码。 */
	public long normalizedPageNum() {
		return pageNum == null || pageNum < 1 ? 1L : pageNum;
	}

	/** 返回1到200之间的分页大小，防止无界查询拖垮数据库。 */
	public long normalizedPageSize() {
		if (pageSize == null || pageSize < 1) {
			return 20L;
		}
		return Math.min(pageSize, 200);
	}

}
