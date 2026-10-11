package com.example.courseschedule.adapter

/**
 * 教务脚本桥接协议说明。
 *
 * 适配脚本在「内置浏览器」中运行，可以：
 *  - 直接访问网页 DOM（document / jQuery / window）
 *  - 通过桥接对象与 App 通信
 *
 * ── 可用桥接接口 ──
 *   window.shiguangBridge.showToast(msg)                    显示提示
 *   window.shiguangBridge.notifyTaskCompletion()            通知任务完成
 *   await window.shiguangBridgePromise.showAlert(t, m, btn) 弹窗（返回是否确认）
 *   await window.shiguangBridgePromise.saveImportedCourses(jsonString)
 *                                                           保存课程（核心）
 *
 * ── 脚本入口 ──
 *   脚本执行后自动运行 runImportFlow()（若存在），
 *   也可由用户在界面上点「执行导入」按钮触发。
 *
 * ── 课程 JSON 字段 ──
 *   [{ "name": "高等数学", "day": 1, "weeks": [1,2,3],
 *      "teacher": "张三", "position": "A101",
 *      "startSection": 1, "endSection": 2 }]
 *   day: 0-6（0=周一）
 *   weeks: 周次数组，或 "1-16" / "单" / "双"
 */
object ScriptBridgeSpec
