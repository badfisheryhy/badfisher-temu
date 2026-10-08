-- 适用：升级尚未包含 execution_id / lease_expires_at 的现有数据库。
-- 执行前必须停止所有旧版本执行器并备份；本脚本仅执行一次。
-- 新建数据库直接执行 schema.sql，不再执行本迁移。
ALTER TABLE tb_temu_order_request_task
    ADD COLUMN execution_id VARCHAR(36) DEFAULT NULL COMMENT '每次领取生成的新执行身份' AFTER state,
    ADD COLUMN lease_expires_at DATETIME(3) DEFAULT NULL COMMENT '数据库时钟维护的执行租约截止时间' AFTER execution_id;

-- 旧进程已全部停止后，将无执行身份的运行中任务转为失败，保留时间窗口供补偿。
UPDATE tb_temu_order_request_task
SET state = 3,
    error_message = '执行身份升级：旧执行器已停止，等待重新领取',
    update_time = CURRENT_TIMESTAMP
WHERE state = 1
  AND execution_id IS NULL
  AND del_flag = 0;
