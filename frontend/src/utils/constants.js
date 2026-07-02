// 全局常量定义
// 说明：与后端枚举一致的部分标注 [后端]；前端扩展字段标注 [前端扩展]，后端实体缺失将无法持久化。

// ============ 路灯状态 [后端] 0-关闭 1-开启 2-故障 ============
export const LIGHT_STATUS = { OFF: 0, ON: 1, FAULT: 2 }

export const LIGHT_STATUS_MAP = {
  0: { label: '关闭', type: 'info' },
  1: { label: '开启', type: 'success' },
  2: { label: '故障', type: 'danger' }
}

// 在线/离线判定（用于实时监测）：status===2 视为故障，无最新数据视为离线
export const ONLINE_STATUS_MAP = {
  online: { label: '在线', type: 'success' },
  offline: { label: '离线', type: 'info' },
  fault: { label: '故障', type: 'danger' }
}

// ============ 行政区 [前端扩展] 后端 light 表无 district 字段 ============
export const DISTRICT_OPTIONS = [
  { value: '城东区', label: '城东区' },
  { value: '城西区', label: '城西区' },
  { value: '城南区', label: '城南区' },
  { value: '城北区', label: '城北区' },
  { value: '中心区', label: '中心区' }
]

// ============ 路段 [前端扩展] 后端 light 表无 road 字段 ============
export const ROAD_OPTIONS = [
  { value: '人民路', label: '人民路' },
  { value: '解放路', label: '解放路' },
  { value: '建设路', label: '建设路' },
  { value: '和平路', label: '和平路' },
  { value: '中山路', label: '中山路' },
  { value: '文化路', label: '文化路' }
]

// 设备类型
export const DEVICE_TYPE_OPTIONS = [
  { value: 'LED-60W', label: 'LED-60W' },
  { value: 'LED-100W', label: 'LED-100W' },
  { value: 'LED-150W', label: 'LED-150W' },
  { value: 'LED-200W', label: 'LED-200W' },
  { value: '钠灯-250W', label: '钠灯-250W(传统基准)' }
]

// 分组维度
export const GROUP_BY_OPTIONS = [
  { value: 'district', label: '行政区' },
  { value: 'road', label: '路段' },
  { value: 'deviceType', label: '设备类型' }
]

// ============ 报警类型 [后端] 1-过流 2-过压 3-欠压 4-过热 5-通讯故障 6-其他 ============
export const ALERT_TYPE_MAP = {
  1: '过流',
  2: '过压',
  3: '欠压',
  4: '过热',
  5: '通讯故障',
  6: '其他'
}

// ============ 报警级别 [后端] 1-提示 2-一般 3-严重 4-紧急 ============
// 需求要求 一般/重要/紧急，映射：2-一般 3-重要 4-紧急
export const ALERT_LEVEL_MAP = {
  1: { label: '提示', type: 'info' },
  2: { label: '一般', type: 'warning' },
  3: { label: '重要', type: 'danger' },
  4: { label: '紧急', type: 'danger' }
}

// ============ 报警处理状态 [后端] 0-未处理 1-已处理 ============
export const ALERT_STATUS_MAP = {
  0: { label: '未处理', type: 'danger' },
  1: { label: '已处理', type: 'success' }
}

// ============ 用户角色 ============
// [后端] admin/operator；[前端扩展] municipal-市政人员（后端 role 为字符串可存储）
export const USER_ROLE_MAP = {
  admin: { label: '系统管理员', type: 'danger' },
  municipal: { label: '市政人员', type: 'warning' },
  operator: { label: '运维人员', type: 'primary' }
}

export const USER_STATUS_MAP = {
  0: { label: '禁用', type: 'info' },
  1: { label: '启用', type: 'success' }
}

// ============ 操作日志类型 [前端扩展] 后端无 operation_log 表 ============
export const OPERATION_TYPE_MAP = {
  switch_on: '远程开灯',
  switch_off: '远程关灯',
  dimming: '调光控制',
  batch_switch: '批量开关',
  strategy_add: '新增定时策略',
  strategy_update: '修改定时策略',
  strategy_delete: '删除定时策略',
  strategy_enable: '启用/停用策略',
  threshold_update: '阈值配置',
  predict_apply: '启用预测调光',
  user_login: '用户登录',
  user_create: '新增用户',
  user_update: '修改用户',
  user_delete: '删除用户',
  config_update: '修改系统参数'
}

// ============ 定时策略 ============
export const WEEKDAY_OPTIONS = [
  { value: 1, label: '周一' },
  { value: 2, label: '周二' },
  { value: 3, label: '周三' },
  { value: 4, label: '周四' },
  { value: 5, label: '周五' },
  { value: 6, label: '周六' },
  { value: 7, label: '周日' }
]

export const STRATEGY_TYPE_MAP = {
  weekday: { label: '工作日', type: 'primary' },
  holiday: { label: '节假日', type: 'warning' },
  everyday: { label: '每日', type: 'success' }
}

// ============ 告警规则类型 [前端扩展] ============
export const ALERT_RULE_TYPE_MAP = {
  offline: '设备离线',
  fault: '灯具故障',
  energy: '能耗异常',
  env: '环境超标',
  overcurrent: '过流',
  overvoltage: '过压',
  undervoltage: '欠压',
  overheat: '过热'
}

// 颜色复用
export const STATUS_COLORS = {
  on: '#67c23a',
  off: '#909399',
  fault: '#f56c6c',
  primary: '#409eff',
  warning: '#e6a23c'
}
