# 山景素材

本轮为贴近用户提供的 App 截图，使用内置 imagegen 工具生成独立山景背景，不从参考截图裁切 UI 或盗用其原图素材。

正式项目文件：

- 原生资源：`D:\codeStudy\code\study.zhzgo.cn_83FYC\apps\android\app\src\main\res\drawable\study_landscape.png`
- 本地预览：`D:\codeStudy\code\study.zhzgo.cn_83FYC\apps\android\design\assets\study-landscape.png`

工具首先将原图保存到固定的 Codex 生成缓存，随后复制到以上 D 盘位置；应用不引用 C 盘缓存。图片作为首页任务背景、无封面时的资源精选/缩略图背景，文字与按钮均由代码绘制。

完整生成提示词（内置工具模式）：

> Use case: photorealistic-natural. Asset type: panoramic mountain photograph to be cropped as a compact mobile learning app banner. Generate a quiet, sophisticated, realistic alpine landscape, soft pale blue-grey hazy sky and distant layered mountains, a sunlit valley and cloud mist in the lower left, dark blue slate dramatic mountain slope on the right. Muted cool slate blue palette with a hint of warm sunrise, natural photographic texture, no oversaturated colors. Composition: landscape 3:2, open light low-detail space across the left half for dark UI text, detailed mountains concentrated in the right third and bottom. The upper-middle horizontal strip should work as a 3:1 banner crop. No words, typography, labels, logos, people, buildings, illustrations, frames, or UI. This is ONLY the background photograph asset, NOT an app mockup.
