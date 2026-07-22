import request from './request'

// 获取语音设置列表
export function getVoiceSetting() {
  return request.get('/broadcast/voice')
}

// 保存语音设置
export function updateVoiceSetting(data) {
  return request.put('/broadcast/voice', data)
}

// TTS 试听：合成文本并返回音频 blob
export function previewTts(text, options = {}) {
  const { voiceName = 'default', speed = 1.0, volume = 1.0 } = options
  return request.post('/broadcast/voice/preview', {
    text,
    voiceName,
    speed,
    volume
  }, {
    responseType: 'blob'
  })
}