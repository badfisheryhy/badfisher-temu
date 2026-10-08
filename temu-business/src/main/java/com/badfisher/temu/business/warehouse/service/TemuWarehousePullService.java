package com.badfisher.temu.business.warehouse.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.badfisher.temu.client.TemuApiClient;
import com.badfisher.temu.business.account.model.TemuAccessToken;
import com.badfisher.temu.business.warehouse.model.TemuWarehouse;
import com.badfisher.temu.client.enums.TemuAuthRegion;
import com.badfisher.temu.client.enums.TemuApiType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Temu 仓库拉取服务 — 调用 bg.logistics.warehouse.list.get 拉取发货仓库列表并落库
 * <p>
 * 响应路径: {@code result.warehouseList}（订单类接口同构，兼容 {@code data.result} 兜底）。 落库策略：按
 * (seller_id, warehouse_id) 唯一索引 upsert，先批量查出已有记录回填 id， 再
 * {@code saveOrUpdateBatch}，避免唯一键冲突。
 *
 * @author lenkor
 */
@Slf4j
@Service
public class TemuWarehousePullService {

	@Resource
	private TemuApiClient temuApiClient;

	@Resource
	private TemuWarehouseService temuWarehouseService;

	@Resource
	private PlatformTransactionManager transactionManager;

	private TransactionTemplate transactionTemplate;

	@PostConstruct
	private void initTransactionTemplate() {
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	/**
	 * 拉取指定店铺的仓库列表并落库。
	 * @return 本次落库条数；接口异常返回 -1
	 */
	@SuppressWarnings("unchecked")
	public int doPullTask(TemuAccessToken requestAccount, TemuAuthRegion authRegion) {
		String sellerId = requestAccount.getSellerId();
		if (sellerId == null) {
			log.warn("仓库拉取缺少sellerId，跳过");
			return -1;
		}
		if (requestAccount.getAppKey() == null || requestAccount.getAppSecret() == null
				|| requestAccount.getAccessToken() == null) {
			log.warn("店铺 {} 授权信息不完整, 跳过仓库拉取", sellerId);
			return -1;
		}

		try {
			Map<String, Object> params = new HashMap<>();
			params.put("type", TemuApiType.LOGISTICS_WAREHOUSE_LIST_GET.getType());

			Map<String, Object> response = temuApiClient.callByAuthRegion(authRegion, params, requestAccount);

			Map<String, Object> result = extractResult(response);
			if (result == null) {
				log.warn("店铺 {} 仓库接口返回无 result 节点", sellerId);
				return -1;
			}

			List<Map<String, Object>> warehouseList = (List<Map<String, Object>>) result.get("warehouseList");
			if (warehouseList == null || warehouseList.isEmpty()) {
				log.info("店铺 {} 仓库接口返回空列表", sellerId);
				return 0;
			}

			List<TemuWarehouse> entities = new ArrayList<>(warehouseList.size());
			for (Map<String, Object> item : warehouseList) {
				TemuWarehouse warehouse = buildWarehouse(item, sellerId);
				if (warehouse.getWarehouseId() == null) {
					log.warn("店铺 {} 仓库记录缺少 warehouseId, 跳过: {}", sellerId, item);
					continue;
				}
				entities.add(warehouse);
			}
			if (entities.isEmpty()) {
				return 0;
			}

			Map<String, TemuWarehouse> existMap = temuWarehouseService
				.list(Wrappers.lambdaQuery(TemuWarehouse.class)
					.eq(TemuWarehouse::getSellerId, sellerId)
					.in(TemuWarehouse::getWarehouseId,
							entities.stream().map(TemuWarehouse::getWarehouseId).collect(Collectors.toSet())))
				.stream()
				.collect(Collectors.toMap(TemuWarehouse::getWarehouseId, w -> w, (a, b) -> a));

			for (TemuWarehouse w : entities) {
				TemuWarehouse exist = existMap.get(w.getWarehouseId());
				if (exist != null) {
					w.setId(exist.getId());
					w.setCreateTime(exist.getCreateTime());
				}
			}

			final List<TemuWarehouse> finalEntities = entities;
			transactionTemplate.executeWithoutResult(status -> temuWarehouseService.saveOrUpdateBatch(finalEntities));

			log.info("店铺 {} 仓库拉取完成: API仓库数={}, 落库总数={}", sellerId, warehouseList.size(), entities.size());
			return entities.size();

		}
		catch (Exception e) {
			log.error("店铺 {} 仓库拉取失败: {}", sellerId, e.getMessage(), e);
			return -1;
		}
	}

	/**
	 * 提取 result 节点 — 主路径 {@code response.result}（与订单类接口同构）， 兼容
	 * {@code response.data.result} 兜底。
	 */
	@SuppressWarnings("unchecked")
	private Map<String, Object> extractResult(Map<String, Object> response) {
		Object resultObj = response.get("result");
		if (resultObj instanceof Map) {
			return (Map<String, Object>) resultObj;
		}
		Object dataObj = response.get("data");
		if (dataObj instanceof Map) {
			Object inner = ((Map<String, Object>) dataObj).get("result");
			if (inner instanceof Map) {
				return (Map<String, Object>) inner;
			}
		}
		return null;
	}

	private TemuWarehouse buildWarehouse(Map<String, Object> map, String sellerId) {
		TemuWarehouse w = new TemuWarehouse();
		w.setSellerId(sellerId);
		w.setWarehouseId(str(map.get("warehouseId")));
		w.setWarehouseName(str(map.get("warehouseName")));
		w.setRegionId1(intVal(map.get("regionId1")));
		w.setWarehouseManagementType(intVal(map.get("warehouseManagementType")));
		w.setDefaultWarehouse(boolVal(map.get("defaultWarehouse")));
		w.setPushOrderToOpenPlatform(boolVal(map.get("pushOrderToOpenPlatform")));
		w.setEnableBuyShippingLabel(boolVal(map.get("enableBuyShippingLabel")));
		return w;
	}

	private static String str(Object value) {
		return value == null ? null : String.valueOf(value);
	}

	private static Integer intVal(Object value) {
		if (value == null)
			return null;
		try {
			return Integer.parseInt(String.valueOf(value));
		}
		catch (Exception e) {
			return null;
		}
	}

	/** JSON 布尔 / 字符串 "true" → 1，否则 0；null 保持 null */
	private static Integer boolVal(Object value) {
		if (value == null)
			return null;
		if (value instanceof Boolean) {
			return ((Boolean) value) ? 1 : 0;
		}
		return "true".equalsIgnoreCase(String.valueOf(value)) ? 1 : 0;
	}

}
