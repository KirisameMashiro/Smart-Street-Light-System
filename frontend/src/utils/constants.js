// 全局常量定义：与后端枚举保持一致

// 路灯状态：0-关闭，1-开启，2-故障
export const LIGHT_STATUS = {
  OFF: 0,
  ON: 1,
  FAULT: 2
}

export const LIGHT_STATUS_MAP = {
  0: { label: '关闭', type: 'info' },
  1: { label: '开启', type: 'success' },
  2: { label: '故障', type: 'danger' }
}

// 报警类型：1-过流，2-过压，3-欠压，4-过热，5-通讯故障，6-其他
export const ALERT_TYPE_MAP = {
  1: '过流',
  2: '过压',
  3: '欠压',
  4: '过热',
  5: '通讯故障',
  6: '其他'
}

// 报警级别：1-提示，2-一般，3-严重，4-紧急
export const ALERT_LEVEL_MAP = {
  1: { label: '提示', type: 'info' },
  2: { label: '一般', type: 'warning' },
  3: { label: '严重', type: 'danger' },
  4: { label: '紧急', type: 'danger' }
}

// 报警处理状态：0-未处理，1-已处理
export const ALERT_STATUS_MAP = {
  0: { label: '未处理', type: 'danger' },
  1: { label: '已处理', type: 'success' }
}

// 用户角色：admin-管理员，operator-运维人员
export const USER_ROLE_MAP = {
  admin: { label: '管理员', type: 'danger' },
  operator: { label: '运维人员', type: 'primary' }
}

// 用户状态：0-禁用，1-启用
export const USER_STATUS_MAP = {
  0: { label: '禁用', type: 'info' },
  1: { label: '启用', type: 'success' }
}
