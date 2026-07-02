import { addOperationLog } from '@/api/operation-log'
import { useUserStore } from '@/store/user'

// 记录操作日志（后端 operation-log 接口缺失时会报错，但不阻断主业务流程）
// type: OPERATION_TYPE_MAP 中的 key；content/result: 描述
export async function logOperation(type, content, result = '成功') {
  const userStore = useUserStore()
  const payload = {
    operator: userStore.user?.username || 'unknown',
    operatorName: userStore.user?.realName || '',
    type,
    content,
    result
  }
  try {
    await addOperationLog(payload)
  } catch (e) {
    // 后端操作日志接口缺失：错误已由拦截器提示，这里不阻断主流程
    console.warn('[操作日志记录失败]', e?.message)
  }
  return payload
}
