Fix these issues IN ORDER. Do NOT stop until ALL are done and pushed:

STEP 1: DELETE the wrong directory
Run: rm -rf app/src/main/java/com/cryptopulse/

STEP 2: ADD anti-whitescreen to index.html
Open index.html and find <div id="loading"> near the top. BEFORE the </head> tag, add these CSS animations:
```css
<style>@keyframes slide{0%{transform:translateX(-100%)}100%{transform:translateX(200%)}}</style>
```
Then right after <body> tag (before <div id="loading">), insert:
```html
<div id="static-fallback" style="position:fixed;top:0;left:0;right:0;bottom:0;z-index:10000;background:#0a1628;display:flex;align-items:center;justify-content:center;flex-direction:column;padding:20px;text-align:center">
  <h2 style="color:#d4a843;font-size:24px;margin-bottom:12px">📈 StockKlineMaster</h2>
  <p style="color:#8899aa;font-size:14px;margin-bottom:16px">K线学习助手 · 加载中...</p>
  <div style="width:120px;height:4px;background:rgba(255,255,255,0.1);border-radius:2px;overflow:hidden">
    <div style="width:60%;height:100%;background:linear-gradient(90deg,#2a5298,#d4a843);border-radius:2px;animation:slide 1.5s ease-in-out infinite"></div>
  </div>
</div>
```
Then find where the app initializes (look for DOMContentLoaded or window.onload or the end of the script). Add this line AFTER all content is rendered:
document.getElementById('static-fallback').style.display='none';

STEP 3: ADD window.onerror handler at the very start of the first <script> block:
```javascript
window.onerror=function(m,t,l){var f=document.getElementById('static-fallback');if(f){f.innerHTML='<h2 style="color:#d4a843">📈 StockKlineMaster</h2><p style="color:#e0555a;font-size:14px">加载异常，请尝试：</p><p style="color:#8899aa;font-size:13px">① 完全关闭APP后重新打开<br>② 清除APP缓存<br>③ 重启手机</p><button onclick="location.reload()" style="margin-top:12px;padding:8px 24px;background:#d4a843;color:#0a1628;border:none;border-radius:6px;font-size:14px;font-weight:700">重试</button>';f.style.display='flex'}return!0};
```

STEP 4: COPY updated index.html to assets
Run: cp index.html app/src/main/assets/index.html

STEP 5: COMMIT and PUSH
Run: git add -A && git commit -m "fix: 清理cryptopulse残留+防白屏三件套+资产同步" && git push

IMPORTANT: Show me git diff after each step so I can verify.
