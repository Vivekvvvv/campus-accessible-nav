/**
 * 语音播报服务 - 基于 Web Speech API
 */

import { logger } from '../utils/logger';

export interface VoiceSettings {
  /** 语速 (0.1 - 10) */
  rate: number;
  /** 音调 (0 - 2) */
  pitch: number;
  /** 音量 (0 - 1) */
  volume: number;
  /** 语言代码 */
  lang: string;
  /** 优先使用的语音名称 */
  preferredVoice?: string;
  /** 是否启用语音播报 */
  enabled: boolean;
}

export interface SpeechQueueItem {
  text: string;
  priority: 'high' | 'normal' | 'low';
  interruptible: boolean;
  onStart?: () => void;
  onEnd?: () => void;
  onError?: (error: string) => void;
}

const DEFAULT_SETTINGS: VoiceSettings = {
  rate: 1.0,
  pitch: 1.0,
  volume: 0.8,
  lang: 'zh-CN',
  enabled: true,
};

class VoiceService {
  private synthesis: SpeechSynthesis | null = null;
  private voices: SpeechSynthesisVoice[] = [];
  private settings: VoiceSettings = { ...DEFAULT_SETTINGS };
  private queue: SpeechQueueItem[] = [];
  private isSpeaking = false;
  private currentUtterance: SpeechSynthesisUtterance | null = null;

  constructor() {
    if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
      this.synthesis = window.speechSynthesis;
      this.loadVoices();

      // 某些浏览器需要监听 voiceschanged 事件
      if (this.synthesis.onvoiceschanged !== undefined) {
        this.synthesis.onvoiceschanged = () => this.loadVoices();
      }
    }

    // 从本地存储加载设置
    this.loadSettings();
  }

  /**
   * 检查语音合成是否可用
   */
  isAvailable(): boolean {
    return this.synthesis !== null;
  }

  /**
   * 加载可用的语音
   */
  private loadVoices(): void {
    if (this.synthesis) {
      this.voices = this.synthesis.getVoices();
      logger.debug(`已加载 ${this.voices.length} 个语音`);
    }
  }

  /**
   * 获取所有可用语音
   */
  getAvailableVoices(): SpeechSynthesisVoice[] {
    return this.voices;
  }

  /**
   * 获取指定语言的语音
   */
  getVoicesForLanguage(lang: string): SpeechSynthesisVoice[] {
    return this.voices.filter(voice => voice.lang.startsWith(lang));
  }

  /**
   * 从本地存储加载设置
   */
  private loadSettings(): void {
    try {
      const saved = localStorage.getItem('voiceSettings');
      if (saved) {
        this.settings = { ...DEFAULT_SETTINGS, ...JSON.parse(saved) };
      }
    } catch (e) {
      logger.warn('加载语音设置失败:', e);
    }
  }

  /**
   * 保存设置到本地存储
   */
  private saveSettings(): void {
    try {
      localStorage.setItem('voiceSettings', JSON.stringify(this.settings));
    } catch (e) {
      logger.warn('保存语音设置失败:', e);
    }
  }

  /**
   * 获取当前设置
   */
  getSettings(): VoiceSettings {
    return { ...this.settings };
  }

  /**
   * 更新设置
   */
  updateSettings(newSettings: Partial<VoiceSettings>): void {
    this.settings = { ...this.settings, ...newSettings };
    this.saveSettings();
  }

  /**
   * 选择最佳语音
   */
  private selectVoice(): SpeechSynthesisVoice | null {
    // 优先使用用户设置的语音
    if (this.settings.preferredVoice) {
      const preferred = this.voices.find(v => v.name === this.settings.preferredVoice);
      if (preferred) return preferred;
    }

    // 然后查找匹配语言的语音
    const langVoices = this.getVoicesForLanguage(this.settings.lang);

    // 优先选择本地语音
    const localVoice = langVoices.find(v => v.localService);
    if (localVoice) return localVoice;

    // 否则返回第一个匹配的
    return langVoices[0] || null;
  }

  /**
   * 播报文本（直接播放，不进入队列）
   */
  speakImmediate(text: string, options?: Partial<SpeechQueueItem>): void {
    if (!this.synthesis || !this.settings.enabled) return;

    // 如果当前正在播报，先停止
    this.stop();

    const utterance = new SpeechSynthesisUtterance(text);
    const voice = this.selectVoice();

    if (voice) {
      utterance.voice = voice;
    }

    utterance.rate = this.settings.rate;
    utterance.pitch = this.settings.pitch;
    utterance.volume = this.settings.volume;
    utterance.lang = this.settings.lang;

    utterance.onstart = () => {
      this.isSpeaking = true;
      this.currentUtterance = utterance;
      options?.onStart?.();
    };

    utterance.onend = () => {
      this.isSpeaking = false;
      this.currentUtterance = null;
      options?.onEnd?.();
      this.processQueue();
    };

    utterance.onerror = (event) => {
      this.isSpeaking = false;
      this.currentUtterance = null;
      options?.onError?.(event.error);
      this.processQueue();
    };

    this.synthesis.speak(utterance);
  }

  /**
   * 添加到播报队列
   */
  speak(text: string, options?: Partial<SpeechQueueItem>): void {
    if (!this.settings.enabled) return;

    const item: SpeechQueueItem = {
      text,
      priority: options?.priority || 'normal',
      interruptible: options?.interruptible ?? true,
      onStart: options?.onStart,
      onEnd: options?.onEnd,
      onError: options?.onError,
    };

    // 高优先级插入队列前面
    if (item.priority === 'high') {
      // 如果当前播报可中断，则中断
      if (this.isSpeaking && this.currentUtterance) {
        const currentInterruptible = this.queue[0]?.interruptible ?? true;
        if (currentInterruptible) {
          this.synthesis?.cancel();
          this.queue.unshift(item);
          this.processQueue();
          return;
        }
      }
      this.queue.unshift(item);
    } else {
      this.queue.push(item);
    }

    if (!this.isSpeaking) {
      this.processQueue();
    }
  }

  /**
   * 处理队列
   */
  private processQueue(): void {
    if (this.queue.length === 0 || this.isSpeaking) return;

    const item = this.queue.shift();
    if (item) {
      this.speakImmediate(item.text, item);
    }
  }

  /**
   * 停止当前播报
   */
  stop(): void {
    if (this.synthesis) {
      this.synthesis.cancel();
      this.isSpeaking = false;
      this.currentUtterance = null;
    }
  }

  /**
   * 清空队列并停止播报
   */
  clearAndStop(): void {
    this.queue = [];
    this.stop();
  }

  /**
   * 暂停播报
   */
  pause(): void {
    if (this.synthesis) {
      this.synthesis.pause();
    }
  }

  /**
   * 恢复播报
   */
  resume(): void {
    if (this.synthesis) {
      this.synthesis.resume();
    }
  }

  /**
   * 是否正在播报
   */
  speaking(): boolean {
    return this.isSpeaking;
  }

  // ========== 导航专用播报方法 ==========

  /**
   * 播报导航指令（高优先级）
   */
  speakNavigationInstruction(instruction: string): void {
    this.speak(instruction, { priority: 'high', interruptible: false });
  }

  /**
   * 播报距离提示
   */
  speakDistanceHint(distance: number, unit: string = '米'): void {
    const text = `距离目的地还有${Math.round(distance)}${unit}`;
    this.speak(text, { priority: 'normal' });
  }

  /**
   * 播报到达提示
   */
  speakArrival(destinationName?: string): void {
    const text = destinationName
      ? `您已到达目的地：${destinationName}`
      : '您已到达目的地';
    this.speak(text, { priority: 'high' });
  }

  /**
   * 播报偏离提示
   */
  speakDeviation(): void {
    this.speak('您已偏离路线，正在重新规划', { priority: 'high' });
  }

  /**
   * 播报紧急求助确认
   */
  speakEmergencyConfirmation(): void {
    this.speak('紧急求助已发出，安保人员正在响应', { priority: 'high' });
  }

  /**
   * 播报无障碍设施信息
   */
  speakFacilityInfo(facilityName: string, distance?: number): void {
    let text = `附近有${facilityName}`;
    if (distance !== undefined) {
      text += `，距离约${Math.round(distance)}米`;
    }
    this.speak(text, { priority: 'normal' });
  }
}

// 导出单例
export const voiceService = new VoiceService();

export default voiceService;
