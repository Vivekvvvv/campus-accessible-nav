import js from '@eslint/js'
import vue from 'eslint-plugin-vue'
import vueParser from 'vue-eslint-parser'
import tsParser from '@typescript-eslint/parser'
import tsPlugin from '@typescript-eslint/eslint-plugin'

export default [
  {
    ignores: [
      '**/node_modules/**',
      '**/dist/**',
      '**/playwright-report/**',
      '**/test-results/**',
      '**/reports/**',
      '**/coverage/**',
    ],
  },
  js.configs.recommended,
  ...vue.configs['flat/recommended'],
  {
    files: ['**/*.vue'],
    languageOptions: {
      parser: vueParser,
      parserOptions: {
        // Let <script lang="ts"> be parsed by TypeScript parser when present.
        parser: tsParser,
        ecmaVersion: 'latest',
        sourceType: 'module',
      },
    },
    rules: {
      // 项目里大量使用 <script setup>，此规则会误报/噪声较大，先关闭
      'vue/multi-word-component-names': 'off',

      // 清理阶段先关掉纯格式噪声（目前 warning 的主要来源）
      'vue/html-indent': 'off',

      // 下面这些规则更偏“风格/约束”，会产生大量噪声；当前阶段聚焦清理无用/重复代码
      'vue/max-attributes-per-line': 'off',
      'vue/singleline-html-element-content-newline': 'off',
      'vue/html-self-closing': 'off',
      'vue/attributes-order': 'off',
      'vue/attribute-hyphenation': 'off',
      'vue/no-v-html': 'off',

      // 该项目以 state 对象下发并在子组件里更新为主，此规则不适配当前架构
      'vue/no-mutating-props': 'off',

      // 模板里大量使用 $emit；要求显式声明对本项目当前价值不高
      'vue/require-explicit-emits': 'off',
    },
  },
  {
    files: ['**/*.js'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
    },
  },
  {
    files: ['**/*.{ts,tsx,d.ts}'],
    languageOptions: {
      parser: tsParser,
      parserOptions: {
        ecmaVersion: 'latest',
        sourceType: 'module',
        ecmaFeatures: {
          jsx: true,
        },
      },
    },
    plugins: {
      '@typescript-eslint': tsPlugin,
    },
    rules: {
      // TypeScript provides its own undefined checks; avoid false positives on DOM/lib types.
      'no-undef': 'off',
      // Prefer TS-aware unused-vars rule.
      'no-unused-vars': 'off',
      '@typescript-eslint/no-unused-vars': ['error', {
        argsIgnorePattern: '^_',
        varsIgnorePattern: '^_',
        caughtErrorsIgnorePattern: '^_',
      }],
    },
  },
  {
    files: ['src/**/*.{js,ts,tsx,vue}'],
    rules: {
      // Keep production console clean: allow warn/error only.
      'no-console': ['error', { allow: ['warn', 'error'] }],
    },
  },

  // Node 环境下的工具/配置文件（避免 `process` 被 no-undef 误报）
  {
    files: ['eslint.config.js', 'vite.config.js', 'playwright.config.js', 'tools/**/*.mjs'],
    languageOptions: {
      globals: {
        process: 'readonly',
      },
    },
  },
]
