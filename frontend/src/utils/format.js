export function formatDateTime(value) {
  if (!value) return '-'
  
  if (Array.isArray(value)) {
    const [year, month, day, hour, minute, second] = value
    const pad = (n) => String(n).padStart(2, '0')
    return `${year}-${pad(month)}-${pad(day)} ${pad(hour)}:${pad(minute)}:${pad(second || 0)}`
  }
  
  const str = String(value).replace('T', ' ')
  return str.length > 19 ? str.substring(0, 19) : str
}

export function formatDate(value) {
  if (!value) return '-'
  return String(value).replace('T', ' ').substring(0, 10)
}

// 转换为后端 LocalDateTime 可接收的字符串格式 yyyy-MM-ddTHH:mm:ss
export function toLocalDateTime(date) {
  if (!date) return null
  const d = new Date(date)
  if (isNaN(d.getTime())) return null
  const pad = (n) => String(n).padStart(2, '0')
  return (
    `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}` +
    `T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
  )
}
