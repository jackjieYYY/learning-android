# 双语 JSON 格式说明

教材格式统一维护在 [EnglishLearning/bilingual-json-format.md](../../EnglishLearning/bilingual-json-format.md)，包括英文句子、毫秒起点、整段中文翻译和可选 `explanation` 老师讲解。

Android 1.3 起读取段落的 `sentences`、`translation` 和可选 `explanation`。长按有讲解段落的英文或中文，或点击“讲解”按钮，显示可滚动的底部讲解面板；打开时暂停视频，关闭后保留位置，也可从本段开始播放。讲解无效时单独忽略，不影响有效字幕。旧版本忽略讲解字段，仍可播放带讲解的新教材。实现与生成流程见 [功能设计](../../EnglishLearning/teacher-explanation-feature.md)。

旧教材无需迁移；讲解缺少或为 `null` 表示没有讲解。新增讲解不能改写英文、翻译或句首时间，整份 UTF-8 JSON 仍限制为 4 MiB。
